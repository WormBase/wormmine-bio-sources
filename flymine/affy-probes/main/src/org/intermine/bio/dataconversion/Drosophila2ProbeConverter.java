package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2010 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

/**
 *
 * @author Julie Sullivan
 */
public class Drosophila2ProbeConverter extends BioFileConverter
{
    protected static final Logger LOG = Logger.getLogger(Drosophila2ProbeConverter.class);

    protected Item dataSource, dataSet, org;
    protected Map<String, String> bioentities = new HashMap<String, String>();
    protected IdResolverFactory resolverFactory;
    private static final String TAXON_ID = "7227";
    private Map<String, String> chromosomes = new HashMap<String, String>();
    private Map<String, ProbeSetHolder> holders = new HashMap<String, ProbeSetHolder>();
    List<Item> delayedItems = new ArrayList<Item>();

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the data model
     * @throws ObjectStoreException if an error occurs in storing
     */
    public Drosophila2ProbeConverter(ItemWriter writer, Model model)
        throws ObjectStoreException {
        super(writer, model, null, null);

        dataSource = createItem("DataSource");
        dataSource.setAttribute("name", "Ensembl");
        store(dataSource);

        org = createItem("Organism");
        org.setAttribute("taxonId", TAXON_ID);
        store(org);

        // only construct factory here so can be replaced by mock factory in tests
        resolverFactory = new FlyBaseIdResolverFactory("gene");
    }

    /**
     * Read each line from flat file.
     *
     * {@inheritDoc}
     */
    public void process(Reader reader)
    throws Exception {

        Iterator<String[]> lineIter = FormattedTextParser.parseTabDelimitedReader(reader);
        boolean hasDataset = false;

        while (lineIter.hasNext()) {
            String[] line = lineIter.next();
            if (!hasDataset) {
                createDataset(line[0]);
                hasDataset = true;
            }

            String probesetIdentifier = line[1];
            String transcriptIdentifier = line[2];
            String fbgn = line[3];
            String chromosomeIdentifier = line[4];
            String startString = line[5];
            String endString = line[6];
            String strand = line[7];

            String chromosomeRefId = createChromosome(chromosomeIdentifier);
            String geneRefId = createGene(fbgn);
            if (geneRefId != null) {
                String transcriptRefId = createBioentity("Transcript", transcriptIdentifier,
                                                         geneRefId);
                ProbeSetHolder holder = getHolder(probesetIdentifier);
                holder.transcripts.add(transcriptRefId);
                holder.genes.add(geneRefId);
                holder.datasets.add(dataSet.getIdentifier());
                try {
                    Integer start = new Integer(startString);
                    Integer end = new Integer(endString);
                    holder.addLocation(chromosomeRefId, start, end, strand);
                } catch (NumberFormatException e) {
                    throw new RuntimeException("bad start/end values");
                }
            }
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    public void close() throws Exception {
        for (ProbeSetHolder holder : holders.values()) {
            storeProbeSet(holder);
        }

        for (Item item : delayedItems) {
            store(item);
        }
    }

    private void storeProbeSet(ProbeSetHolder holder)
    throws ObjectStoreException  {
        Item probeSet = createItem("ProbeSet");
        probeSet.setAttribute("primaryIdentifier", holder.probesetIdentifier);
        probeSet.setAttribute("name", holder.probesetIdentifier);
        probeSet.setReference("organism", org.getIdentifier());
        probeSet.setCollection("dataSets", holder.datasets);
        probeSet.setCollection("transcripts", holder.transcripts);
        probeSet.setCollection("locations", holder.createLocations(probeSet.getIdentifier()));
        probeSet.setCollection("genes", holder.genes);
        super.createSynonym(probeSet.getIdentifier(), "identifier", holder.probesetIdentifier,
                null);
        store(probeSet);
    }

    /**
     * Holds information about the probeset until all probes have been processed and we know the
     * start and end
     * @author Julie Sullivan
     */
    public class ProbeSetHolder
    {
        protected String probesetIdentifier;
        protected List<String> genes = new ArrayList<String>();
        protected List<String> transcripts = new ArrayList<String>();
        private List<String> locations = new ArrayList<String>();
        protected Map<String, LocationHolder> locationHolders
        = new HashMap<String, LocationHolder>();
        protected List<String> datasets = new ArrayList<String>();

        /**
         * @param identifier probeset identifier
         */
        public ProbeSetHolder(String identifier) {
            probesetIdentifier = identifier;
        }

        /**
         * @param chromosomeRefId id representing a chromosome object
         * @param start start of location
         * @param end end of location
         * @param strand strand, eg -1 or 1
         */
        protected void addLocation(String chromosomeRefId, Integer start, Integer end,
                                   String strand) {
            String key = chromosomeRefId + "|" + start.toString() + "|"
            + end.toString() + "|" + strand;
            if (locationHolders.get(key) == null) {
                LocationHolder location = new LocationHolder(chromosomeRefId, start, end, strand);
                locationHolders.put(key, location);
            }
        }

        /**
         * when all of the probes for this probeset have been processed, create and store all
         * related locations
         * @param probeSetRefId id representing probeset object
         * @return reference list of location objects
         * @throws ObjectStoreException if something goes wrong storing locations
         */
        protected List<String> createLocations(String probeSetRefId)
        throws ObjectStoreException {
            for (LocationHolder holder : locationHolders.values()) {
                String location = createLocation(holder, probeSetRefId);
                locations.add(location);
            }
            return locations;
        }
    }

    /**
     * holds information about a location
     */
    public class LocationHolder
    {
        protected Integer start = new Integer(-1);
        protected Integer end = new Integer(-1);
        protected String strand;
        protected String chromosomeRefID;

        /**
         * @param chromosomeRefId id representing a chromosome object
         * @param start start of location
         * @param end end of location
         * @param strand strand, eg -1 or 1
         */
        public LocationHolder(String chromosomeRefId, Integer start, Integer end, String strand) {
            this.chromosomeRefID = chromosomeRefId;
            this.start = start;
            this.end = end;
            this.strand = strand;
        }
    }

    private String createGene(String id)
    throws ObjectStoreException {
        String identifier = id;
        IdResolver resolver = resolverFactory.getIdResolver();
        int resCount = resolver.countResolutions(TAXON_ID, identifier);
        if (resCount != 1) {
            LOG.info("RESOLVER: failed to resolve gene to one identifier, ignoring gene: "
                     + identifier + " count: " + resCount + " FBgn: "
                     + resolver.resolveId(TAXON_ID, identifier));
            return null;
        }
        identifier = resolver.resolveId(TAXON_ID, identifier).iterator().next();
        return createBioentity("Gene", identifier, null);
    }

    private String createBioentity(String type, String identifier, String geneRefId)
    throws ObjectStoreException {
        String refId = bioentities.get(identifier);
        if (refId == null) {
            Item bioentity = createItem(type);
            bioentity.setAttribute("primaryIdentifier", identifier);
            bioentity.setReference("organism", org.getIdentifier());
            if (type.equals("Transcript")) {
                bioentity.setReference("gene", geneRefId);
            }
            bioentity.addToCollection("dataSets", dataSet);
            refId = bioentity.getIdentifier();
            store(bioentity);
            bioentities.put(identifier, refId);
            createSynonym(refId, "identifier", identifier);
        }
        return refId;
    }

    private void createDataset(String array)
    throws ObjectStoreException  {
        dataSet = createItem("DataSet");
        dataSet.setReference("dataSource", dataSource.getIdentifier());
        dataSet.setAttribute("name", "Affymetrix array: " + array);
        store(dataSet);
    }

    private String createChromosome(String identifier)
    throws ObjectStoreException {
        String refId = chromosomes.get(identifier);
        if (refId == null) {
            Item item = createItem("Chromosome");
            item.setAttribute("primaryIdentifier", identifier);
            item.setReference("organism", org.getIdentifier());
            chromosomes.put(identifier, item.getIdentifier());
            store(item);
            refId = item.getIdentifier();
        }
        return refId;
    }


    private String createLocation(LocationHolder holder, String probeset)
    throws ObjectStoreException {
        Item item = createItem("Location");
        item.setAttribute("start", holder.start.toString());
        item.setAttribute("end", holder.end.toString());
        if (holder.strand != null) {
            item.setAttribute("strand", holder.strand);
        } else {
            LOG.warn("probeset " + probeset + " has no strand");
        }
        item.setReference("locatedOn", holder.chromosomeRefID);
        item.setReference("feature", probeset);
        item.addToCollection("dataSets", dataSet);
        store(item);
        return item.getIdentifier();
    }

    private ProbeSetHolder getHolder(String identifier) {
        ProbeSetHolder holder = holders.get(identifier);
        if (holder == null) {
            holder = new ProbeSetHolder(identifier);
            holders.put(identifier, holder);
        }
        return holder;
    }
}

