package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2005 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.Reader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.StringTokenizer;
import java.util.Properties;
import java.util.Enumeration;

import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.PropertiesUtil;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.ReferenceList;
import org.intermine.xml.full.ItemHelper;
import org.intermine.xml.full.ItemFactory;
import org.intermine.dataconversion.FileConverter;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;

/**
 * DataConverter to parse an INPARANOID Orthologue/Paralogue "sqltable" data file into Items
 * @author Mark Woodbridge
 * @author Richard Smith
 */
public class InparanoidConverter extends FileConverter
{
    protected static final String GENOMIC_NS = "http://www.flymine.org/model/genomic#";

    protected static final String PROP_FILE = "inparanoid_config.properties";
    protected Map bioEntities = new HashMap();
    protected Item db, analysis;
    protected Map ids = new HashMap();
    protected Map organisms = new LinkedHashMap();
    protected ItemFactory itemFactory;
    protected Map sources = new LinkedHashMap();
    protected Map orgSources = new HashMap();
    protected Map taxonIds = new HashMap();

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @throws ObjectStoreException if an error occurs in storing
     */
    public InparanoidConverter(ItemWriter writer) throws ObjectStoreException {
        super(writer);
        itemFactory = new ItemFactory(Model.getInstanceByName("genomic"));
        setupItems();

        readConfig();
    }

    private void readConfig() {
        Properties props = new Properties();
        try {
            props.load(getClass().getClassLoader().getResourceAsStream(PROP_FILE));
        } catch (IOException e) {
            throw new RuntimeException("Problem loading properties '" + PROP_FILE + "'", e);
        }
        Enumeration propNames = props.propertyNames();
        while (propNames.hasMoreElements()) {
            String code = (String) propNames.nextElement();
            code = code.substring(0, code.indexOf("."));
            Properties codeProps = PropertiesUtil.stripStart(code,
                PropertiesUtil.getPropertiesStartingWith(code, props));
            String taxonId = codeProps.getProperty("taxonid");
            if (taxonId == null) {
                System.out.println("taxonIds: " + taxonIds);
                System.out.println("orgSources: " + orgSources);
                throw new IllegalArgumentException("Unable to find taxonId property for code: "
                                                   + code + " in file: " + PROP_FILE);
            }
            taxonId = taxonId.trim();
            String source = codeProps.getProperty("source");
            if (taxonId == null) {
                System.out.println("taxonIds: " + taxonIds);
                System.out.println("orgSources: " + orgSources);
                throw new IllegalArgumentException("Unable to find source property for code: "
                                                   + code + " in file: " + PROP_FILE);
            }
            source = source.trim();
            taxonId = taxonId.trim();
            code = code.trim();
            taxonIds.put(code, taxonId);
            orgSources.put(taxonId, source);
        }
    }

    /**
     * @see FileConverter#process(Reader)
     */
    public void process(Reader reader) throws Exception {
        int lineNum = 0;
        String line, species = null, oldIndex = null;
        Item gene = null, trans = null;

        BufferedReader br = new BufferedReader(reader);
        while ((line = br.readLine()) != null) {
            lineNum++;
            String[] array = line.split("\t");

            if (array.length != 4 && array.length < 8) {
                throw new IllegalArgumentException("Line " + lineNum
                                                   + " does not have at least eight elements: "
                                                   + line);
            }

            String type = null;

            String index = array[0];
            String geneId = null;
            String transId = null;

            // some files have gene id, no translations
            // TODO this should be specified in the config files
            if ("modSC".equals(array[2]) || "modRR".equals(array[2]) || "modMM".equals(array[2])
                || "modSP".equals(array[2]) || "sanPF".equals(array[2])) {
                geneId = array[4];
            } else {
                transId = array[4];
            }

            // for ensembl data we can create corresponding genes, parse id from additional info
            if (array[2].startsWith("ens")) {
                String info = array[7];
                StringTokenizer tok = new StringTokenizer(info, " ");
                while (tok.hasMoreTokens()) {
                    String bit = (String) tok.nextToken().trim();
                    if (bit.startsWith("gene:")) {
                        geneId = bit.substring(5);
                    }
                }
            }

            if (!index.equals(oldIndex)) {
                // clear old values and try to set new ones
                gene = null;
                trans = null;
                oldIndex = index;
                species = array[2];
                if (transId != null) {
                    trans = newBioEntity(transId, getOrganism(species), "Translation");
                }
                if (geneId != null) {
                    gene = newBioEntity(geneId, getOrganism(species), "Gene");
                }
                continue;
            }

            Item newTrans = null;
            if (transId != null) {
                newTrans = newBioEntity(transId, getOrganism(array[2]), "Translation");
            }

            Item newGene = null;
            if (geneId != null) {
                newGene = newBioEntity(geneId, getOrganism(array[2]), "Gene");
            }
            Item result = newResult(array[3]);

            // create two orthologues/paralogues with subject[Translation] and
            // object[Translation] reversed
            Item item1 = createItem(species.equals(array[2]) ? "Paralogue" : "Orthologue");
            Item item2 = createItem(species.equals(array[2]) ? "Paralogue" : "Orthologue");

            if (gene != null) {
                item1.setReference("object", gene.getIdentifier());
                item2.setReference("subject", gene.getIdentifier());
            }
            if (trans != null) {
                item1.setReference("objectTranslation", trans.getIdentifier());
                item2.setReference("subjectTranslation", trans.getIdentifier());

            }
            if (newGene != null) {
                item1.setReference("subject", newGene.getIdentifier());
                item2.setReference("object", newGene.getIdentifier());
            }
            if (newTrans != null) {
                item1.setReference("subjectTranslation", newTrans.getIdentifier());
                item2.setReference("objectTranslation", newTrans.getIdentifier());

            }

            item1.addCollection(new ReferenceList("evidence", Arrays.asList(new Object[]
                {db.getIdentifier(), result.getIdentifier()})));
            writer.store(ItemHelper.convert(item1));
            item2.addCollection(new ReferenceList("evidence", Arrays.asList(new Object[]
                {db.getIdentifier(), result.getIdentifier()})));
            writer.store(ItemHelper.convert(item2));

            if (!species.equals(array[2])) {
                species = array[2];
                gene = newGene;
                trans = newTrans;
            }
        }
    }

    /**
     * @see FileConverter#close()
     */
    public void close() throws ObjectStoreException {
        store(organisms.values());
        store(bioEntities.values());
        store(sources.values());
    }

    private String newId(String className) {
        Integer id = (Integer) ids.get(className);
        if (id == null) {
            id = new Integer(0);
            ids.put(className, id);
        }
        id = new Integer(id.intValue() + 1);
        ids.put(className, id);
        return id.toString();
    }

    /**
     * Convenience method to create and cache Genes/Proteins by identifier
     * @param identifier identifier for the new Gene/Protein
     * @param organism the Organism for this protein
     * @param type create either a Gene or Translation
     * @return a new Gene/Protein Item
     * @throws ObjectStoreException if an error occurs in storing
     */
    protected Item newBioEntity(String identifier, Item organism, String type)
        throws ObjectStoreException {
        // HACK mouse and rat identifiers should have 'MGI:' and 'RGD:' at the start
        String taxonId = organism.getAttribute("taxonId").getValue();
        if (taxonId.equals("10116")) {
            identifier = "RGD:" + identifier;
        } else if (taxonId.equals("10090")) {
            identifier = "MGI:" + identifier;
        }

        // lookup by identifier and type, sometimes same id for translation and gene
        String key = type + identifier;
        if (bioEntities.containsKey(key)) {
            return (Item) bioEntities.get(key);
        }

        Item item = createItem(type);
        item.setAttribute("identifier", identifier);
        item.setReference("organism", organism.getIdentifier());
        bioEntities.put(key, item);

        // create a synonm - lookup source according to organism
        Item synonym = createItem("Synonym");
        synonym.setAttribute("type", "identifier");
        synonym.setAttribute("value", identifier);
        synonym.setReference("subject", item.getIdentifier());
        Item source = getSourceForOrganism(organism.getAttribute("taxonId").getValue());
        synonym.setReference("source", source.getIdentifier());
        writer.store(ItemHelper.convert(synonym));

        return item;
    }

    // get source for synonyms, depends on organism
    private Item getSourceForOrganism(String taxonId) {
        String sourceName = (String) orgSources.get(taxonId);
        if (sourceName == null) {
                throw new IllegalArgumentException("unable to find source name for organism: "
                                                   + taxonId);
        }
        Item source = (Item) sources.get(sourceName);
        if (source == null) {
            source = createItem("DataSource");
            source.setAttribute("name", sourceName);
            sources.put(sourceName, source);
        }
        return source;
    }

    /**
     * Convenience method to create a new analysis result
     * @param confidence the INPARANOID confidence for the result
     * @return a new ComputationalResult Item
     * @throws ObjectStoreException if an error occurs in storing
     */
    protected Item newResult(String confidence) throws ObjectStoreException {
        Item item = createItem("ComputationalResult");
        item.setAttribute("confidence", confidence);
        item.setReference("analysis", analysis.getIdentifier());
        writer.store(ItemHelper.convert(item));
        return item;
    }

    private Item getOrganism(String code) {
        if (code.length() != 5) {
            throw new IllegalArgumentException("invalid organism code: " + code);
        }

        String taxonId = (String) taxonIds.get(code);
        if (taxonId == null) {
            throw new IllegalArgumentException("Unable to find taxonId for code: "
                                               + code + ", check properties: " + PROP_FILE);
        }

        Item organism = (Item) organisms.get(taxonId);
        if (organism == null) {
            organism = createItem("Organism");
            organism.setAttribute("taxonId", taxonId);
            organisms.put(taxonId, organism);
        }
        return organism;
    }

    /**
     * Set up the items that are common to all orthologues/paralogues
     * @throws ObjectStoreException if an error occurs in storing
     */
    protected void setupItems() throws ObjectStoreException {
        Item pub = createItem("Publication");
        pub.setAttribute("pubMedId", "11743721");

        analysis = createItem("ComputationalAnalysis");
        analysis.setAttribute("algorithm", "InParanoid");
        analysis.setReference("publication", pub.getIdentifier());

        db = createItem("DataSet");
        db.setAttribute("title", "InParanoid data set");

        List toStore = Arrays.asList(new Object[] {db, analysis, pub});
        for (Iterator i = toStore.iterator(); i.hasNext();) {
            writer.store(ItemHelper.convert((Item) i.next()));
        }
    }

    /**
     * Convenience method for creating a new Item
     * @param className the name of the class
     * @return a new Item
     */
    protected Item createItem(String className) {
        return itemFactory.makeItem(alias(className) + "_" + newId(className),
                                    GENOMIC_NS + className, "");
    }


}

