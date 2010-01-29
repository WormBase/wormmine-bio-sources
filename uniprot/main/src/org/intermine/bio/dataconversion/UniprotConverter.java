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

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.dataconversion.DirectoryConverter;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.SAXParser;
import org.intermine.util.StringUtil;
import org.intermine.util.Util;
import org.intermine.xml.full.Item;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


/**
 * DataConverter to parse UniProt data into items.  Improved version of UniProtConverter.
 *
 * Differs from UniProtConverter in that this Converter creates proper protein items.
 * UniProtConverter creates protein objects, that are really uniprot entries.
 * @author Julie Sullivan
 */
public class UniprotConverter extends DirectoryConverter
{
    private static final UniprotConfig CONFIG = new UniprotConfig();
    private static final Logger LOG = Logger.getLogger(UniprotConverter.class);
    private Map<String, String> pubs = new HashMap<String, String>();
    private Map<String, String> organisms = new HashMap<String, String>();
    private Map<String, String> comments = new HashMap<String, String>();
    private Map<String, String> datasets = new HashMap<String, String>();
    private Map<String, String> synonyms = new HashMap<String, String>();
    private Map<String, String> domains = new HashMap<String, String>();
    private Map<String, List<String>> sequences = new HashMap<String, List<String>>();
    private Map<String, String> datasources = new HashMap<String, String>();
    private Map<String, String> ontologies = new HashMap<String, String>();
    private Map<String, String> keywords = new HashMap<String, String>();
    private Map<String, String> genes = new HashMap<String, String>();
    private Map<String, String> goterms = new HashMap<String, String>();

    // don't allow duplicate identifiers
    private List<String> geneIdentifiers = new ArrayList<String>();

    private boolean createInterpro = false;
    private boolean creatego = false;
    private Set<String> taxonIds = null;

    protected IdResolverFactory resolverFactory;
    private IdResolver flyResolver;
    private String datasourceRefId = null;

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public UniprotConverter(ItemWriter writer, Model model) {
        super(writer, model);
        // only construct factory here so can be replaced by mock factory in tests
        resolverFactory = new FlyBaseIdResolverFactory("gene");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(File dataDir) throws Exception {
        try {
            datasourceRefId = getDataSource("UniProt");
            setOntology("UniProtKeyword");
        } catch (SAXException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        Map<String, File[]> taxonIdToFiles = parseFileNames(dataDir.listFiles());


        if (taxonIds != null) {
            for (String taxonId : taxonIds) {
                if (taxonIdToFiles.get(taxonId) == null) {
                    throw new RuntimeException("no files found for " + taxonId);
                }
                processFiles(taxonIdToFiles.get(taxonId));
            }
        } else {
            // if files aren't in taxonId_sprot|trembl format, assume they are in uniprot_sprot.xml 
            // format  
            File[] files = dataDir.listFiles();
            File[] sortedFiles = new File[2];
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                String filename = file.getName();
                // process sprot, then trembl
                if (filename.equals("uniprot_sprot.xml")) {
                    sortedFiles[0] = file;
                } else if (filename.equals("uniprot_trembl.xml")) {
                    sortedFiles[1] = file;
                }
            }
            processFiles(sortedFiles);
        }
    }

    // process the sprot file, then the trembl file
    private void processFiles(File[] files) 
    throws SAXException {
        for (int i = 0; i <= 1; i++) {
            File file = files[i];
            if (file == null) {
                continue;
            }
            UniprotHandler handler = new UniprotHandler();
            try {
                System.out.println("Processing file: " + file.getPath());
                Reader reader = new FileReader(file);
                SAXParser.parse(new InputSource(reader), handler);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } 
        // reset all variables here, new organism
        sequences = new HashMap<String, List<String>>();
        genes = new HashMap<String, String>();
    }

    /*
     * sprot data files need to be processed immediately before trembl ones
     * not all organisms are going to have both files
     *
     * UniProtFilterTask has already been run so all files are assumed to be valid.
     *
     *  expected syntax : 7227_uniprot_sprot.xml
     *  [TAXONID]_uniprot_[SOURCE].xml
     *  SOURCE: sprot or trembl
     */
    private Map<String, File[]> parseFileNames(File[] fileList) {
        Map<String, File[]> files = new HashMap<String, File[]>();
        if (fileList == null) {
            return null;
        }
        for (File file : fileList) {
            String[] bits = file.getName().split("_");
            String taxonId = bits[0];
            if (bits.length != 3) {
                LOG.info("Bad file found:  "  + file.getName()
                        + ", expected a filename like 7227_uniprot_sprot.xml");
                continue;
            }
            String source = bits[2].replace(".xml", "");
            // process trembl last because trembl has duplicates of sprot proteins
            if (!source.equals("sprot") && !source.equals("trembl")) {
                LOG.info("Bad file found:  "  + file.getName()
                        +  " (" + bits[2] + "), expecting sprot or trembl ");
                continue;
            }
            int i = (source.equals("sprot") ? 0 : 1);
            if (!files.containsKey(taxonId)) {
                File[] sourceFiles = new File[2];
                sourceFiles[i] = file;
                files.put(taxonId, sourceFiles);
            } else {
                files.get(taxonId)[i] = file;
            }
        }
        return files;
    }

    /**
     * Toggle whether or not to import interpro data
     * @param createinterpro String whether or not to import interpro data (true/false)
     */
    public void setCreateinterpro(String createinterpro) {
        if (createinterpro.equals("true")) {
            this.createInterpro = true;
        } else {
            this.createInterpro = false;
        }
    }

    /**
     * Toggle whether or not to import GO data
     * @param creatego whether or not to import GO terms (true/false)
     */
    public void setCreatego(String creatego) {
        if (creatego.equals("true")) {
            this.creatego = true;
        } else {
            this.creatego = false;
        }
    }

    /**
     * Sets the list of taxonIds that should be imported if using split input files.
     *
     * @param taxonIds a space-separated list of taxonIds
     */
    public void setUniprotOrganisms(String taxonIds) {
        this.taxonIds = new HashSet<String>(Arrays.asList(StringUtil.split(taxonIds, " ")));
        LOG.info("Setting list of organisms to " + this.taxonIds);
    }



    /* converts the XML into UniProt entry objects.  run once per file */
    private class UniprotHandler extends DefaultHandler
    {
        private UniprotEntry entry;
        private Stack<String> stack = new Stack<String>();
        private String attName = null;
        private StringBuffer attValue = null;
        private String taxonId = null;
        private Map<String, String> synonyms = new HashMap<String, String>();
        
        private int entryCount = 0;
        
        /**
         * @param entries empty map to be populated with uniprot entries
         * @param isoforms empty map to be populated with isoforms
         */
        public UniprotHandler() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs)
        throws SAXException {
            attName = null;
            if (qName.equals("entry")) {
                entry = new UniprotEntry();
                entry.setDatasetRefId(getDataset(getAttrValue(attrs, "dataset")));
//            } else if (qName.equals("protein")) {
//                String isFragment = "false";
//                if (getAttrValue(attrs, "type") != null
//                       && getAttrValue(attrs, "type").startsWith("fragment")) {
//                    isFragment = "true";
//                }
//                entry.setFragment(isFragment);
            } else if (qName.equals("fullName") && stack.search("protein") == 2
                    &&  (stack.peek().equals("recommendedName")
                            || stack.peek().equals("submittedName"))) {
                attName = "proteinName";
            } else if ((qName.equals("fullName") || qName.equals("shortName"))
                    && stack.search("protein") == 2
                    && (stack.peek().equals("alternativeName")
                            || stack.peek().equals("recommendedName")
                            || stack.peek().equals("submittedName"))) {
                attName = "synonym";
            } else if (qName.equals("fullName")
                    && stack.peek().equals("recommendedName")
                    && stack.search("component") == 2) {
                attName = "component";
            } else if (qName.equals("name") && stack.peek().equals("entry")) {
                attName = "primaryIdentifier";
            } else if (qName.equals("accession")) {
                attName = "value";
            } else if (qName.equals("dbReference") && stack.peek().equals("organism")) {
                entry.setTaxonId(getAttrValue(attrs, "id"));
            } else if (qName.equals("id")  && stack.peek().equals("isoform")) {
                attName = "isoform";
            } else if (qName.equals("sequence")  && stack.peek().equals("isoform")) {
                String sequenceType = getAttrValue(attrs, "type");
                // ignore "external" types
                if (sequenceType.equals("displayed")) {
                    entry.setCanonicalIsoform(entry.getAttribute());
                } else if (sequenceType.equals("described")) {
                    entry.addIsoform(entry.getAttribute());
                }
            } else if (qName.equals("sequence")) {
                String strLength = getAttrValue(attrs, "length");
                String strMass = getAttrValue(attrs, "mass");
                if (strLength != null) {
                    entry.setLength(strLength);
                    attName = "residues";
                }
                if (strMass != null) {
                    entry.setMolecularWeight(strMass);
                }
                // fragments - we probably don't load any of these
                String isFragment = "false";
                if (getAttrValue(attrs, "fragment") != null) {
                    isFragment = "true";
                }
                entry.setFragment(isFragment);
            } else if (qName.equals("feature") && getAttrValue(attrs, "type") != null) {
                Item feature = getFeature(getAttrValue(attrs, "type"), getAttrValue(attrs,
                "description"), getAttrValue(attrs, "status"));
                entry.addFeature(feature);
            } else if ((qName.equals("begin") || qName.equals("end"))
                    && entry.processingFeature()
                    && getAttrValue(attrs, "position") != null) {
                entry.addFeatureLocation(qName, getAttrValue(attrs, "position"));
            } else if (qName.equals("position") && entry.processingFeature()
                    && getAttrValue(attrs, "position") != null) {
                entry.addFeatureLocation("begin", getAttrValue(attrs, "position"));
                entry.addFeatureLocation("end", getAttrValue(attrs, "position"));
            } else if (createInterpro && qName.equals("dbReference")
                    && getAttrValue(attrs, "type").equals("InterPro")) {
                entry.addAttribute(getAttrValue(attrs, "id"));
            } else if (createInterpro && qName.equals("property") && entry.processing()
                    && stack.peek().equals("dbReference")
                    && getAttrValue(attrs, "type").equals("entry name")) {
                String domain = entry.getAttribute();
                if (domain.startsWith("IPR")) {
                    entry.addDomainRefId(getInterpro(domain, getAttrValue(attrs, "value"),
                            entry.getDatasetRefId()));
                }
            } else if (qName.equals("dbReference") && stack.peek().equals("organism")) {
                taxonId = getAttrValue(attrs, "id");
                entry.setTaxonId(taxonId);
            } else if (qName.equals("dbReference") && stack.peek().equals("citation")
                    && getAttrValue(attrs, "type").equals("PubMed")) {
                entry.addPub(getPub(getAttrValue(attrs, "id")));
            } else if (qName.equals("comment") && getAttrValue(attrs, "type") != null
                    && !getAttrValue(attrs, "type").equals("")) {
                entry.setCommentType(getAttrValue(attrs, "type"));
            } else if (qName.equals("text") && stack.peek().equals("comment")
                    && entry.processing()) {
                attName = "text";
            } else if (qName.equals("keyword")) {
                attName = "keyword";
            } else if (qName.equals("dbReference") && stack.peek().equals("entry")) {
                entry.addDbref(getAttrValue(attrs, "type"), getAttrValue(attrs, "id"));
            } else if (qName.equals("property") && stack.peek().equals("dbReference")) {
                // if the dbref has no gene designation value, it is discarded.
                // without the gene designation, it's impossible to match up identifiers with the
                // correct genes
                String type = getAttrValue(attrs, "type");
                // TODO put text in config file
                if (type != null && type.equals("gene designation")) {
                    String value = getAttrValue(attrs, "value");
                    entry.addGeneDesignation(value);
                }
            } else if (qName.equals("name") && stack.peek().equals("gene")) {
                attName = getAttrValue(attrs, "type");
            } else if (qName.equals("dbreference") || qName.equals("comment")
                    || qName.equals("isoform")
                    || qName.equals("gene")) {
                // set temporary holder variables to null
                entry.reset();
            }
            super.startElement(uri, localName, qName, attrs);
            stack.push(qName);
            attValue = new StringBuffer();
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public void endElement(String uri, String localName, String qName)
        throws SAXException {
            super.endElement(uri, localName, qName);
            stack.pop();
            if (attName == null || attValue.toString() == null) {
                return;
            }
            if (qName.equals("sequence")) {
                setSequence(entry, attValue.toString().replaceAll("\n", ""));
            } else if (attName.equals("proteinName")) {
                entry.setName(attValue.toString());
            } else if (attName.equals("synonym")) {
                entry.addProteinName(attValue.toString());
            } else if (qName.equals("text") && stack.peek().equals("comment")) {
                String commentText = attValue.toString();
                if (commentText != null  & !commentText.equals("")) {
                    entry.addCommentRefId(getComment(entry.getCommentType(), commentText));
                    entry.setCommentType(null);
                }
            } else if (qName.equals("name") && stack.peek().equals("gene")) {
                String type = attName;
                String name = attValue.toString();
                // See #1199 - remove organism prefixes ("AgaP_" or "Dmel_")
                name = name.replaceAll("^[A-Z][a-z][a-z][A-Za-z]_", "");
                entry.addGene(type, name);
            } else if (qName.equals("keyword")) {
                entry.addKeyword(getKeyword(attValue.toString()));
            } else if (attName.equals("primaryIdentifier")) {
                entry.setPrimaryIdentifier(attValue.toString());
            } else if (qName.equals("accession")) {
                entry.addAccession(attValue.toString());
            } else if (attName.equals("component") && qName.equals("fullName")
                            && stack.peek().equals("recommendedName")
                            && stack.search("component") == 2) {
                entry.addComponent(attValue.toString());
            } else if (qName.equals("id") && stack.peek().equals("isoform")) {
                String accession = attValue.toString();

                // 119 isoforms have commas in their IDs
                if (accession.contains(",")) {
                    String[] accessions = accession.split("[, ]+");
                    accession = accessions[0];
                    for (int i = 1; i < accessions.length; i++) {
                        entry.addIsoformSynonym(accessions[i]);
                    }
                }

                // attribute should be empty, unless isoform has two <id>s
                if (entry.getAttribute() == null) {
                    entry.addAttribute(accession);
                } else {
                    // second <id> value is ignored and added as a synonym
                    entry.addIsoformSynonym(accession);
                }
            } else if (qName.equals("entry")) {
                Set<UniprotEntry> isoforms = processEntry(entry);
                for (UniprotEntry isoform : isoforms) {
                    processEntry(isoform);
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void characters(char[] ch, int start, int length) {
            int st = start;
            int l = length;
            if (attName != null) {

                // DefaultHandler may call this method more than once for a single
                // attribute content -> hold text & create attribute in endElement
                while (l > 0) {
                    boolean whitespace = false;
                    switch(ch[st]) {
                    case ' ':
                    case '\r':
                    case '\n':
                    case '\t':
                        whitespace = true;
                        break;
                    default:
                        break;
                    }
                    if (!whitespace) {
                        break;
                    }
                    ++st;
                    --l;
                }

                if (l > 0) {
                    StringBuffer s = new StringBuffer();
                    s.append(ch, st, l);
                    attValue.append(s);
                }
            }
        }


        private Set<UniprotEntry> processEntry(UniprotEntry entry)
        throws SAXException {

            entryCount++;
            
            if (entryCount % 1000 == 0) {
                logMapSizes();
            }
            
            Set<UniprotEntry> isoforms = new HashSet<UniprotEntry>();

            // TODO there are uniparc entries so check for swissprot-trembl datasets
            if (entry.hasDatasetRefId() && entry.hasPrimaryAccession() && !entry.isDuplicate()) {

                for (String isoformAccession: entry.getIsoforms()) {
                    isoforms.add(entry.clone(isoformAccession));
                }

                Item protein = createItem("Protein");

                /* primaryAccession, primaryIdentifier, name, etc */
                processIdentifiers(protein, entry);

                String isCanonical = (entry.isIsoform() ? "false" : "true");
                protein.setAttribute("isUniprotCanonical", isCanonical);

                /* dataset */
                protein.addToCollection("dataSets", entry.getDatasetRefId());

                /* sequence */
                if (!entry.isIsoform()) {
                    processSequence(protein, entry);
                }

                /* interpro */
                if (createInterpro && !entry.getDomains().isEmpty()) {
                    protein.setCollection("proteinDomains", entry.getDomains());
                }

                /* organism */
                try {
                    protein.setReference("organism", getOrganism(entry.getTaxonId()));
                } catch (SAXException e) {
                    throw new RuntimeException("store failed for " + entry.getPrimaryAccession());
                }

                /* publications */
                if (!entry.getPubs().isEmpty()) {
                    protein.setCollection("publications", entry.getPubs());
                }

                /* comments */
                if (!entry.getComments().isEmpty()) {
                    protein.setCollection("comments", entry.getComments());
                }

                /* keywords */
                if (!entry.getKeywords().isEmpty()) {
                    protein.setCollection("keywords", entry.getKeywords());
                }

                /* features */
                processFeatures(protein, entry);

                /* components */
                if (!entry.getComponents().isEmpty()) {
                    processComponents(protein, entry);
                }

                List<String> synonymRefIds = new ArrayList<String>();
                try {
                    /* dbrefs (go terms, refseq) */
                    processDbrefs(protein, entry, synonymRefIds);

                    /* genes */
                    processGene(protein, entry);

                    store(protein);

                    processSynonyms(synonymRefIds, protein.getIdentifier(), entry);

                } catch (ObjectStoreException e) {
                    throw new SAXException(e);
                }
                synonyms = new HashMap<String, String>();
            }
            return isoforms;
        }

        private void logMapSizes() {
            StringBuffer sb = new StringBuffer();
            String endl = System.getProperty("line.separator");
            sb.append("pubs: " + pubs.size() + endl);
            sb.append("organisms: " + organisms.size() + endl);
            sb.append("comments: " + comments.size() + endl);
            sb.append("synonyms: " + synonyms.size() + endl);
            sb.append("datasets: " + datasets.size() + endl);
            sb.append("domains: " + domains.size() + endl);
            sb.append("sequences: " + sequences.size() + endl);
            sb.append("datasources: " + datasources.size() + endl);
            sb.append("ontologies: " + ontologies.size() + endl);
            sb.append("keywords: " + keywords.size() + endl);
            sb.append("genes: " + genes.size() + endl);
            sb.append("goterms: " + goterms.size() + endl);
            sb.append("geneIdentifiers: " + geneIdentifiers.size() + endl);
            sb.append("stack: " + stack.size() + endl);
            LOG.info("Processed " + entryCount + " entries: " + endl + sb.toString());
        }
        
        private void processSequence(Item protein, UniprotEntry entry) {
            protein.setAttribute("length", entry.getLength());
            protein.setReference("sequence", entry.getSeqRefId());
            protein.setAttribute("molecularWeight", entry.getMolecularWeight());
            protein.setAttribute("md5checksum", entry.getMd5checksum());
        }

        private void processIdentifiers(Item protein, UniprotEntry entry) {
            protein.setAttribute("name", entry.getName());
            protein.setAttribute("isFragment", entry.isFragment());
            protein.setAttribute("uniprotAccession", entry.getUniprotAccession());
            String primaryAccession = entry.getPrimaryAccession();
            protein.setAttribute("primaryAccession", primaryAccession);

            String primaryIdentifier = entry.getPrimaryIdentifier();
            protein.setAttribute("uniprotName", primaryIdentifier);

            // primaryIdentifier must be unique, so append isoform suffix, eg -1
            if (entry.isIsoform()) {
                primaryIdentifier = getIsoformIdentifier(primaryAccession, primaryIdentifier);
            }
            protein.setAttribute("primaryIdentifier", primaryIdentifier);
        }

        private String getIsoformIdentifier(String primaryAccession, String primaryIdentifier) {
            String isoformIdentifier = primaryIdentifier;
            String[] bits = primaryAccession.split("\\-");
            if (bits.length == 2) {
                isoformIdentifier += "-" + bits[1];
            }
            return isoformIdentifier;
        }

        private void processComponents(Item protein, UniprotEntry entry)
        throws SAXException {
            for (String componentName : entry.getComponents()) {
                Item component = createItem("Component");
                component.setAttribute("name", componentName);
                component.setReference("protein", protein);
                try {
                    store(component);
                } catch (ObjectStoreException e) {
                    throw new SAXException(e);
                }
            }
        }


        private void processFeatures(Item protein, UniprotEntry entry) 
        throws SAXException {
            for (Item feature : entry.getFeatures()) {
                feature.setReference("protein", protein);            
                try {
                    store(feature);
                } catch (ObjectStoreException e) {
                    throw new SAXException(e);
                }
            }
        }

        private void processSynonyms(List<String> proteinSynonyms, String proteinRefId,
                UniprotEntry entry)
        throws SAXException {

            String datasetRefId = entry.getDatasetRefId();

            // primary accession
            String refId = getSynonym(proteinRefId, "accession", entry.getPrimaryAccession(),
                    "true", datasetRefId);
            proteinSynonyms.add(refId);

            // accessions
            for (String accession : entry.getAccessions()) {
                refId = getSynonym(proteinRefId, "accession", accession, "false", datasetRefId);
                proteinSynonyms.add(refId);
            }

            // primaryIdentifier
            String primaryIdentifier = entry.getPrimaryIdentifier();
            refId = getSynonym(proteinRefId, "identifier", primaryIdentifier, "false",
                    datasetRefId);
            proteinSynonyms.add(refId);

            // primaryIdentifier if isoform
            if (entry.isIsoform()) {
                String isoformIdentifier
                = getIsoformIdentifier(entry.getPrimaryAccession(), entry.getPrimaryIdentifier());
                refId = getSynonym(proteinRefId, "identifier", isoformIdentifier, "false",
                        datasetRefId);
                proteinSynonyms.add(refId);
            }

            // name <recommendedName> or <alternateName>
            for (String name : entry.getProteinNames()) {
                refId = getSynonym(proteinRefId, "name", name, "false", datasetRefId);
                proteinSynonyms.add(refId);
            }

            // duplicate trembl entries
            if (!entry.isIsoform() && entry.getMd5checksum() != null
                            && !sequences.get(entry.getMd5checksum()).isEmpty()) {
                for (String synonym : sequences.get(entry.getMd5checksum())) {
                    refId = getSynonym(proteinRefId, "accession", synonym, "false", datasetRefId);
                    proteinSynonyms.add(refId);
                }
            }

            // isoforms with extra identifiers
            List<String> isoformSynonyms = entry.getIsoformSynonyms();
            if (!isoformSynonyms.isEmpty()) {
                for (String synonym : isoformSynonyms) {
                    refId = getSynonym(proteinRefId, "accession", synonym, "false", datasetRefId);
                    proteinSynonyms.add(refId);
                }
            }
        }

        private void processDbrefs(Item protein, UniprotEntry entry, List<String> synonymRefIds)
        throws SAXException {
            Map<String, List<String>> dbrefs = entry.getDbrefs();

            for (Map.Entry<String, List<String>> dbref : dbrefs.entrySet()) {

                String key = dbref.getKey();
                List<String> values = dbref.getValue();

                if (key.equals("EC")) {
                    protein.setAttribute("ecNumber", values.get(0));
                } else if (key.equals("RefSeq")) {
                    for (String synonym : values) {
                        String refId = getSynonym(protein.getIdentifier(), "identifier", synonym, 
                                "false", entry.getDatasetRefId());
                        synonymRefIds.add(refId);
                    }
                } else if (creatego && key.equals("GO")) {
                    for (String identifier : values) {
                        entry.addGOTerm(getGoTerm(identifier));
                    }
                }
            }        
        }




        private void processGoAnnotation(UniprotEntry entry, Item gene) 
        throws SAXException {
            for (String goTermRefId : entry.getGOTerms()) {
                Item goAnnotation = createItem("GOAnnotation");
                goAnnotation.setReference("subject", gene);
                goAnnotation.setReference("ontologyTerm", goTermRefId);
                gene.addToCollection("goAnnotation", goAnnotation);
                try {
                    store(goAnnotation);
                } catch (ObjectStoreException e) {
                    throw new SAXException(e);
                }
            }
        }

        // gets the unique identifier and list of identifiers to set
        // loops through each gene entry, assigns refId to protein
        private void processGene(Item protein, UniprotEntry entry)
        throws SAXException {
            String taxonId = entry.getTaxonId();

            // which gene.identifier field has to be unique
            String uniqueIdentifierField = CONFIG.getUniqueIdentifier(taxonId);
            if (uniqueIdentifierField == null) {
                uniqueIdentifierField = CONFIG.getUniqueIdentifier("default");
            }

            // for this organism, set the following gene fields
            Set<String> geneFields = CONFIG.getGeneIdentifierFields(taxonId);
            if (geneFields == null) {
                geneFields = CONFIG.getGeneIdentifierFields("default");
            }

            // just one gene, don't have to worry about gene designations and dbrefs
            if (!entry.hasMultipleGenes()) {
                String geneRefId = createGene(entry, taxonId, geneFields, uniqueIdentifierField);
                if (geneRefId != null) {
                    protein.addToCollection("genes", geneRefId);
                }
                return;
            }

            // loop through each gene entry to be processed
            // cloning the gene removes dbrefs without gene designations
            List<UniprotEntry> clonedEntries = entry.cloneGenes();
            Iterator<UniprotEntry> iter = clonedEntries.iterator();
            while (iter.hasNext()) {
                // create a dummy entry and add identifiers for specific gene
                String geneRefId = createGene(iter.next(), taxonId, geneFields,
                        uniqueIdentifierField);
                if (StringUtils.isNotEmpty(geneRefId)) {
                    protein.addToCollection("genes", geneRefId);
                }
            }
        }

        // creates and stores the gene
        // sets the identifier fields specified in the config file
        // creates synonym
        private String createGene(UniprotEntry entry, String taxonId, Set<String> geneFields,
                String uniqueIdentifierFieldType)
        throws SAXException {

            List<String> geneSynonyms = new ArrayList<String>();

            String uniqueIdentifierValue = getGeneIdentifier(entry, taxonId,
                    uniqueIdentifierFieldType, geneSynonyms, true);
            if (uniqueIdentifierValue == null) {
                return null;
            }
            String geneRefId = genes.get(uniqueIdentifierValue);
            if (geneRefId == null) {
                Item gene = createItem("Gene");
                genes.put(uniqueIdentifierValue, gene.getIdentifier());
                gene.addToCollection("dataSets", entry.getDatasetRefId());
                gene.setAttribute(uniqueIdentifierFieldType, uniqueIdentifierValue);

                // set each identifier
                for (String geneField : geneFields) {
                    if (geneField.equals(uniqueIdentifierFieldType)) {
                        // we've already set the key field
                        continue;
                    }
                    String identifier = getGeneIdentifier(entry, taxonId, geneField, geneSynonyms,
                            false);

                    if (identifier == null) {
                        LOG.error("Couldn't process gene, no " + geneField);
                        continue;
                    }

                    /*
                     * if the protein is an isoform, this gene has already been processed so the
                     * identifier will always be a duplicate in this case.
                     */
                    if (!entry.isIsoform() && geneIdentifiers.contains(identifier)) {
                        LOG.error("not assigning duplicate identifier:  " + identifier);
                        continue;
                        // if the canonical protein is processed and the gene has a duplicate
                        // identifier, we need to flag so the gene won't be created for the isoform
                        // either.
                    }
                    geneIdentifiers.add(identifier);
                    gene.setAttribute(geneField, identifier);
                }

                if (creatego) {
                    processGoAnnotation(entry, gene);
                }
                
                // store gene
                try {
                    gene.setReference("organism", getOrganism(taxonId));
                    store(gene);
                } catch (ObjectStoreException e) {
                    throw new SAXException(e);
                }

                // synonyms
                geneRefId = gene.getIdentifier();
                for (String identifier : geneSynonyms) {
                    getSynonym(geneRefId, "identifier", identifier, null, entry.getDatasetRefId());
                }
                getSynonym(geneRefId, "identifier", uniqueIdentifierValue, null,
                           entry.getDatasetRefId());
            }
            return geneRefId;
        }

        // gets the identifier for a gene from the dbref/names collected from the XML
        // which identifier is chosen depends on the configuration in the uniprot config file
        private String getGeneIdentifier(UniprotEntry entry, String taxonId, String identifierType,
                                         List<String> geneSynonyms, boolean isUniqueIdentifier) {

            String identifierValue = null;
            // how to get the identifier, eg. dbref OR name
            String method = CONFIG.getIdentifierMethod(taxonId, identifierType);
            // what value to use with method, eg. "FlyBase" or "ORF"
            String value = CONFIG.getIdentifierValue(taxonId, identifierType);

            if (method == null || value == null) {
                // use default set in config file, if this organism isn't configured
                method = CONFIG.getIdentifierMethod("default", identifierType);
                value = CONFIG.getIdentifierValue("default", identifierType);
                if (method == null || value == null) {
                    throw new RuntimeException("error processing line in config file for organism "
                                               + taxonId);
                }
            }

            if (method.equals("name")) {
                if (entry.getGeneNames() == null || entry.getGeneNames().isEmpty()) {
                    LOG.error("No gene names for " + taxonId + ". protein accession:"
                              + entry.getPrimaryAccession());
                    return null;
                }
                identifierValue = entry.getGeneNames().get(value);
            } else if (method.equals("dbref")) {
                if (value.equals("Ensembl")) {
                    // See #2122
                    identifierValue = entry.getGeneDesignation("Ensembl");   
                } else {
                    Map<String, List<String>> dbrefs = entry.getDbrefs();
                    String msg = "no " + value + " identifier found for gene attached to protein: " 
                                    + entry.getPrimaryAccession();
                    if (dbrefs == null || dbrefs.isEmpty()) {
                        LOG.error(msg);
                        return null;
                    }
                    List<String> identifiers = dbrefs.get(value);
                    if (identifiers == null || identifiers.isEmpty()) {
                        LOG.error(msg);
                        return null;
                    }
                    // TODO handle multiple identifiers somehow
                    identifierValue = entry.getDbrefs().get(value).get(0);
                }
            } else {
                LOG.error("error processing line in config file for organism " + taxonId);
                return null;
            }
            geneSynonyms.add(identifierValue);
            if (isUniqueIdentifier && taxonId.equals("7227")) {
                identifierValue = resolveGene(taxonId, identifierValue);

                // try again!
                if (identifierValue == null && entry.getGeneNames() != null
                                && !entry.getGeneNames().isEmpty()) {
                    Iterator<String> iter = entry.getGeneNames().values().iterator();
                    while (iter.hasNext() && identifierValue == null) {
                        identifierValue = resolveGene(taxonId, iter.next());
                    }
                }
            }
            return identifierValue;
        }

        private String resolveGene(String taxonId, String identifier) {
            flyResolver = resolverFactory.getIdResolver(false);
            if (flyResolver == null) {
                // no id resolver available, so return the original identifier
                return identifier;
            }
            int resCount = flyResolver.countResolutions(taxonId, identifier);
            if (resCount != 1) {
                LOG.info("RESOLVER: failed to resolve gene to one identifier, ignoring gene: "
                         + identifier + " count: " + resCount + " FBgn: "
                         + flyResolver.resolveId(taxonId, identifier));
                return null;
            }
            return flyResolver.resolveId(taxonId, identifier).iterator().next();
        }
    }

    private void setSequence(UniprotEntry entry, String sequence)
    throws SAXException {
        String md5checksum = Util.getMd5checksum(sequence);
        if (!sequences.containsKey(md5checksum)) {
            entry.setDuplicate(false);
            entry.setMd5checksum(md5checksum);
            sequences.put(md5checksum, new ArrayList<String>());
            Item item = createItem("Sequence");
            item.setAttribute("residues", sequence);
            item.setAttribute("length", entry.getLength());
            entry.setSeqRefId(item.getIdentifier());
            try {
                store(item);
            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
        } else {
            // duplicate trembl protein
            entry.setDuplicate(true);
            sequences.get(md5checksum).addAll(entry.getSynonyms());
        }
    }

    private String getDataSource(String title)
    throws SAXException {
        String refId = datasources.get(title);
        if (refId == null) {
            Item item = createItem("DataSource");
            item.setAttribute("name", title);
            refId = item.getIdentifier();
            datasources.put(title, refId);
            try {
                store(item);
            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
        }
        return refId;
    }

    private String getKeyword(String title)
    throws SAXException {
        String refId = keywords.get(title);
        if (refId == null) {
            Item item = createItem("OntologyTerm");
            item.setAttribute("name", title);
            item.setReference("ontology", ontologies.get("UniProtKeyword"));
            refId = item.getIdentifier();
            keywords.put(title, refId);
            try {
                store(item);
            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
        }
        return refId;
    }

    private String getComment(String commentType, String text)
    throws SAXException {
        String key = commentType + text;
        String refId = comments.get(key);
        if (refId == null) {
            Item item = createItem("Comment");
            item.setAttribute("type", commentType);
            item.setAttribute("text", text);
            refId = item.getIdentifier();
            comments.put(key, refId);
            try {
                store(item);
            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
        }
        return refId;
    }

    private String getInterpro(String identifier, String shortName, String datasetRefId)
    throws SAXException {
        String refId = domains.get(identifier);
        if (refId == null) {
            Item item = createItem("ProteinDomain");
            item.setAttribute("primaryIdentifier", identifier);
            item.setAttribute("shortName", shortName);
            item.addToCollection("dataSets", datasetRefId);
            refId = item.getIdentifier();
            domains.put(identifier, refId);
            try {
                store(item);
            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
            getSynonym(refId, "identifier", identifier, "true", datasetRefId);
            getSynonym(refId, "name", shortName, "false", datasetRefId);
        }
        return refId;
    }

    private String getOrganism(String taxonId)
    throws SAXException {
        String refId = organisms.get(taxonId);
        if (refId == null) {
            Item item = createItem("Organism");
            item.setAttribute("taxonId", taxonId);
            refId = item.getIdentifier();
            organisms.put(taxonId, refId);
            try {
                store(item);
            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
        }
        return refId;
    }

    private String getSynonym(String subjectId, String type, String value, String isPrimary,
                              String datasetRefId)
    throws SAXException {
        String key = type + subjectId + value;
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        String refId = synonyms.get(key);
        if (refId == null) {
            Item item = createItem("Synonym");
            item.setReference("subject", subjectId);
            item.setAttribute("type", type);
            item.setAttribute("value", value);
            item.addToCollection("dataSets", datasetRefId);
            if (isPrimary != null) {
                item.setAttribute("isPrimary", isPrimary);
            }
            refId = item.getIdentifier();
            try {
                store(item);
            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
            synonyms.put(key, refId);
        }
        return refId;
    }

    private String getPub(String pubMedId)
    throws SAXException {
        String refId = pubs.get(pubMedId);

        if (refId == null) {
            Item item = createItem("Publication");
            item.setAttribute("pubMedId", pubMedId);
            pubs.put(pubMedId, item.getIdentifier());
            try {
                store(item);
            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
            refId = item.getIdentifier();
        }

        return refId;
    }

    private String getDataset(String title)
    throws SAXException {
        String refId = datasets.get(title);
        if (refId == null) {
            Item item = createItem("DataSet");
            item.setAttribute("title", title + " data set");
            item.setReference("dataSource", datasourceRefId);
            refId = item.getIdentifier();
            datasets.put(title, refId);
            try {
                store(item);
            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
        }
        return refId;
    }

    private String getGoTerm(String identifier)
    throws SAXException {
        String refId = goterms.get(identifier);
        if (refId == null) {
            Item item = createItem("GOTerm");
            item.setAttribute("identifier", identifier);            
            refId = item.getIdentifier();
            goterms.put(identifier, refId);
            try {
                store(item);
            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
        }
        return refId;
    }
    
    private String setOntology(String title)
    throws SAXException {
        String refId = ontologies.get(title);
        if (refId == null) {
            Item ontology = createItem("Ontology");
            ontology.setAttribute("title", title);
            ontologies.put(title, ontology.getIdentifier());
            try {
                store(ontology);
            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
        }
        return refId;
    }

    private Item getFeature(String type, String description, String status)
    throws SAXException {
        List<String> featureTypes = CONFIG.getFeatureTypes();
        if (featureTypes.isEmpty() || featureTypes.contains(type)) {
            Item feature = createItem("UniProtFeature");
            feature.setAttribute("type", type);
            String keywordRefId = getKeyword(type);
            feature.setReference("feature", keywordRefId);
            String featureDescription = description;
            if (status != null) {
                featureDescription = (description == null ? status : description
                                                          + " (" + status + ")");
            }
            if (!StringUtils.isEmpty(featureDescription)) {
                feature.setAttribute("description", featureDescription);
            }
            return feature;
        }
        return null;
    }

    /**
     * Get a value from SAX attributes and trim() the returned string.
     * @param attrs SAX Attributes map
     * @param name the attribute to fetch
     * @return attValue
     */
    private String getAttrValue(Attributes attrs, String name) {
        if (attrs.getValue(name) != null) {
            return attrs.getValue(name).trim();
        }
        return null;
    }
}
