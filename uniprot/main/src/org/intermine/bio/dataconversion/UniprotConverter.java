package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2007 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;

import org.intermine.dataconversion.FileConverter;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.PropertiesUtil;
import org.intermine.util.SAXParser;
import org.intermine.util.StringUtil;
import org.intermine.xml.full.Attribute;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.ItemFactory;
import org.intermine.xml.full.ItemHelper;
import org.intermine.xml.full.Reference;
import org.intermine.xml.full.ReferenceList;

import java.io.IOException;
import java.io.Reader;

import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


/**
 * DataConverter to parse UniProt data into items
 * @author Richard Smith
 */
public class UniprotConverter extends FileConverter
{
    private Map mapMaster = new HashMap();  // map of maps
    //TODO: This should come from props files!!!!
    protected static final String GENOMIC_NS = "http://www.flymine.org/model/genomic#";
    protected static final String PROP_FILE = "uniprot_config.properties";
    private static final Logger LOG = Logger.getLogger(UniprotConverter.class);
    private Map pubMaster = new HashMap();
    private Map orgMaster = new HashMap();
    private Map dbMaster = new HashMap();
    private Map dsMaster = new HashMap();
    private Map ontoMaster = new HashMap();
    private Map featureTypes = new HashMap();
    private Map geneMaster = new HashMap();
    private Map interproMaster = new HashMap();
    private Set geneIdentifiers = new HashSet();
    private Map geneDataMaps = new HashMap();   // map of taxonId to object which determine
                                                // which data to use for which organism
    private Set geneSources = new HashSet();    // datasources that designate gene names
                                                // e.g. WormBase, Ensembl
    private Map ids = new HashMap();
    private Map aliases = new HashMap();
    private Map keyMaster = new HashMap();
    private boolean createInterpro = false;
    private static final List<String> GENE_PREFIXES_TO_STRIP = 
        Arrays.asList(new String[] {"AgaP_"});
    private Set<String> taxonIds = null;
    private Set<String> doneTaxonIds = new HashSet();
    private boolean useSplitFiles = false;
    
    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @throws ObjectStoreException if an error occurs in storing
     */
    public UniprotConverter(ItemWriter writer) throws ObjectStoreException {
        super(writer);
    }


    /**
     * @see FileConverter#process(Reader)
     */
    public void process(Reader reader) throws Exception {
        boolean doProcess = true;
        if (useSplitFiles) {
            doProcess = false;
            String fileName = getCurrentFile().getName();
            String taxonId = fileName.substring(0, fileName.indexOf("_"));
            if (taxonIds.contains(taxonId)) {
                doProcess = true;
                doneTaxonIds.add(taxonId);
            } else {
                System.err.println("Not processing " + fileName + " - not in list of organisms.");
            }
        }

        if (doProcess) {
            mapMaps();
            readConfig();
            mapFeatures();
            UniprotHandler handler = new UniprotHandler(getItemWriter(), mapMaster, createInterpro);

            try {
                SAXParser.parse(new InputSource(reader), handler);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * {@inheritDocs}
     */
    public void close() throws Exception {
        if (useSplitFiles) {
            if (!doneTaxonIds.containsAll(taxonIds)) {
                throw new Exception("Did not process all required taxonIds. Required = " + taxonIds
                        + ", done = " + doneTaxonIds);
            }
        }
    }

    private void mapMaps() {

        mapMaster.put("pubMaster", pubMaster);
        mapMaster.put("orgMaster", orgMaster);
        mapMaster.put("dbMaster", dbMaster);
        mapMaster.put("featureTypes", featureTypes);
        mapMaster.put("dsMaster", dsMaster);
        mapMaster.put("ontoMaster", ontoMaster);
        mapMaster.put("geneMaster", geneMaster);
        mapMaster.put("interproMaster", interproMaster);
        mapMaster.put("geneDataMaps", geneDataMaps);
        mapMaster.put("geneIdentifiers", geneIdentifiers);
        mapMaster.put("geneSources", geneSources);
        mapMaster.put("ids", ids);
        mapMaster.put("aliases", aliases);
        mapMaster.put("keyMaster", keyMaster);

    }

    private void mapFeatures() {

        featureTypes.put("INIT_MET", "initiator methionine");    // VDAC_DROME
        featureTypes.put("SIGNAL", "signal peptide");            // AMYA_DROME
        featureTypes.put("PROPEP", "propeptide");                // ACES_DROME
        featureTypes.put("MOTIF", "short sequence motif");       // A4_DROME
        featureTypes.put("TRANSIT", "transit peptide");          // MMSA_DROME
        featureTypes.put("CHAIN", "chain");                      // ADCY2_DROME
        featureTypes.put("PEPTIDE", "peptide");                  // CCAP_DROME
        featureTypes.put("TOPO_DOM", "topological domain");      // 5HT2A_DROME
        featureTypes.put("TRANSMEM", "transmembrane region");    // 5HT2A_DROME
        featureTypes.put("ACT_SITE", "active site");             // AMYA_DROME
        featureTypes.put("METAL", "metal ion-binding site");     // ADCY2_DROME
        featureTypes.put("BINDING", "binding site");             // MMSA_DROME
        featureTypes.put("SITE", "site");                        // VDAC_DROME
        featureTypes.put("MOD_RES", "modified residue");         // AMYA_DROME
        featureTypes.put("LIPID", "lipid moiety-binding region"); // ACES_DROME
        featureTypes.put("CARBOHYD", "glycosylation site");      // 5HT2A_DROME
        featureTypes.put("VAR_SEQ", "splice variant");           // zen
        featureTypes.put("VARIANT", "sequence variant");         // AMYA_DROME
        featureTypes.put("UNSURE", "unsure residue");            // none

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
                throw new IllegalArgumentException("Unable to find 'taxonid' property for " 
                                                 + "code: " + code + " in file: " + PROP_FILE);
            }
            taxonId = taxonId.trim();
            
            /* which variable to use as the uniqueGeneIdentifier */
            String attribute = codeProps.getProperty("attribute");
            if (attribute == null) {
                throw new IllegalArgumentException("Unable to find 'attribute' property for " 
                                                 + "code: " + code + " in file: " + PROP_FILE);
            }
            
            attribute = attribute.trim();
            UniProtGeneDataMap geneDataMap = new UniProtGeneDataMap(attribute);
            
            /* Sources appear as source for gene name synonym and 
             * are used for links out from the webapp. */
            String source = codeProps.getProperty("source");
            if (source != null) {
                source = source.trim();
                geneDataMap.setSource(source);
                geneSources.add(source);
            }
            
            /* which variable to use as the geneOrganismDbId */
            String organismDbIdSrcType = codeProps.getProperty("organismDbIdSrcType");
            if (organismDbIdSrcType != null) {
                organismDbIdSrcType = organismDbIdSrcType.trim();
                String organismDbIdSrc = codeProps.getProperty("organismDbIdSrc");
                if (organismDbIdSrc == null) {
                    throw new IllegalArgumentException("Unable to find 'organismDbIdSrc' property " 
                                                     + "for code: " + code + " in file: " 
                                                     + PROP_FILE);
                }
                organismDbIdSrc = organismDbIdSrc.trim();
                geneDataMap.setOrganismDbId(organismDbIdSrcType, organismDbIdSrc);
                if (organismDbIdSrcType.equals("datasource")) {
                    geneSources.add(organismDbIdSrc);
                }
            }
            
            /* which variable to use as the geneIdentifier */
            String identifierSrcType = codeProps.getProperty("identifierSrcType");
            if (identifierSrcType != null) {
                identifierSrcType = identifierSrcType.trim();
                String identifierSrc = codeProps.getProperty("identifierSrc");                
                if (identifierSrc == null) {
                    throw new IllegalArgumentException("Unable to find 'identifierSrc' property for"
                                                     + " code: " + code + " in file: " + PROP_FILE);
                }
                identifierSrc = identifierSrc.trim();
                geneDataMap.setIdentifier(identifierSrcType, identifierSrc);     
                if (identifierSrcType.equals("datasource")) {
                    geneSources.add(identifierSrc);
                }
            }            
            geneDataMaps.put(taxonId, geneDataMap);          
        }
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
     * Sets the list of taxonIds that should be imported if using split input files.
     *
     * @param taxonIds a space-separated list of taxonIds
     */
    public void setUniprotOrganisms(String taxonIds) {
        this.taxonIds = new HashSet(Arrays.asList(StringUtil.split(taxonIds, " ")));
        System.err.println("Setting list of organisms to " + this.taxonIds);
    }

    /**
     * Sets the parameter that this will read the split uniprot files.
     *
     * @param p ignored
     */
    public void setUseSplitFiles(String p) {
        useSplitFiles = true;
    }

    /**
     * Extension of PathQueryHandler to handle parsing TemplateQueries
     */
    static class UniprotHandler extends DefaultHandler
    {
        private int nextClsId = 0;
        private ItemFactory itemFactory;

        // the below are reset for each protein
        private Item protein;
        private Item sequence;
        private Item comment;
        private Item feature;
        private Item interpro;  // protein feature
        private Map synonyms;
        private Map genes;
        private StringBuffer descr;
        private String taxonId;
        private String dbName;
        private String evidence;
        private boolean hasPrimary;

        private ReferenceList pubCollection;
        private ReferenceList commentCollection;
        private ReferenceList keywordCollection;
        private ReferenceList featureCollection;
        private ReferenceList geneCollection;
        private ReferenceList interproCollection;

        // maps genes for this protein to that gene's lists of names, identifiers, etc
        private Map geneTOgeneNameTypeToName;
        private Map geneTOgeneDesignations;

        // reset for each gene
        private Map geneNameTypeToName;      // ORF, primary, etc value for gene name
        private Set geneNames;               // list of names for this gene
        private Map geneDesignations;        // gene names from each database
        private String possibleGeneIdSource; // ie FlyBase, Ensemble, etc.
        private String possibleGeneId;       // temp holder for gene identifier
                                             // until "gene designation" is verified on next line

        // master lists - only one is created
        private Map pubMaster;
        private Map orgMaster;
        private Map dbMaster;
        private Map dsMaster;
        private Map ontoMaster;
        private Map interproMaster;
        private Map featureTypes;
        private Item datasource;
        private Item dataset;
        private Map geneMaster;       // itemID to gene
        private Set geneIdentifiers;  // all gene identifiers
        private Map geneDataMaps;
        private Set geneSources;
        private Map ids;
        private Map aliases;
        private Map keyMaster;

        private ItemWriter writer;

        private Stack stack = new Stack();
        private String attName = null;
        private StringBuffer attValue = null;
        private boolean createInterpro = false;

        /**
         * Constructor
         * @param writer the ItemWriter used to handle the resultant items
         * @param mapMaster the Map of maps
         * @param createInterpro whether or not to create interpro items
         */
        public UniprotHandler(ItemWriter writer, Map mapMaster, boolean createInterpro) {

            itemFactory = new ItemFactory(Model.getInstanceByName("genomic"));
            this.writer = writer;

            this.pubMaster = (Map) mapMaster.get("pubMaster");
            this.orgMaster = (Map) mapMaster.get("orgMaster");
            this.dbMaster = (Map) mapMaster.get("dbMaster");
            this.dsMaster = (Map) mapMaster.get("dsMaster");
            this.ontoMaster = (Map) mapMaster.get("ontoMaster");
            this.interproMaster = (Map) mapMaster.get("interproMaster");
            this.featureTypes = (Map) mapMaster.get("featureTypes");
            this.geneMaster = (Map) mapMaster.get("geneMaster");
            this.geneIdentifiers = (Set) mapMaster.get("geneIdentifiers");
            this.geneDataMaps = (Map) mapMaster.get("geneDataMaps");
            this.geneSources = (Set) mapMaster.get("geneSources");
            this.ids = (Map) mapMaster.get("ids");
            this.aliases = (Map) mapMaster.get("aliases");
            this.keyMaster = (Map) mapMaster.get("keyMaster");
            this.createInterpro = createInterpro;
        }


        /**
         * {@inheritDoc}
         */
        public void startElement(String uri, String localName, String qName, Attributes attrs)
            throws SAXException {
            attName = null;
            try {
                // <entry>
                if (qName.equals("entry")) {
                    // create, clear all lists for each new protein
                    initProtein();
                // <entry><protein>
                } else if (qName.equals("protein")) {
                    String isFragment = "false";
                    // check for <protein type="fragment*">
                    if (attrs.getValue("type") != null) {
                        String type = attrs.getValue("type");
                        if (type.startsWith("fragment")) {
                            isFragment = "true";
                        }
                    }
                    protein.setAttribute("isFragment", isFragment);
                // <entry><protein><name>
                } else if (qName.equals("name") && stack.peek().equals("protein")) {
                    attName = "name";
                    evidence = attrs.getValue("evidence");
                // <entry><name>
                } else if (qName.equals("name") && stack.peek().equals("entry")) {
                    attName = "identifier";
                // <entry><accession>
                } else if (qName.equals("accession")) {
                    attName = "value";
                // <entry><sequence>
                } else if (qName.equals("sequence")) {
                    String strLength = attrs.getValue("length");
                    String strMass = attrs.getValue("mass");
                    if (strLength != null) {
                        sequence = createItem("Sequence");
                        sequence.setAttribute("length", strLength);
                        protein.setAttribute("length", strLength);
                        attName = "residues";
                    }
                    if (strMass != null) {
                        protein.setAttribute("molecularWeight", strMass);
                    }
                // <entry><feature>
                } else if (qName.equals("feature")
                                && attrs.getValue("type") != null
                                && featureTypes.containsValue(attrs.getValue("type"))) {

                    String strType = attrs.getValue("type");
                    String strName = attrs.getValue("description");
                    String strStatus = null;
                    feature = createItem("UniProtFeature");
                    feature.addReference(new Reference("protein", protein.getIdentifier()));
                    if (featureCollection.getRefIds().isEmpty()) {
                        protein.addCollection(featureCollection);
                    }
                    featureCollection.addRefId(feature.getIdentifier());
                    feature.setAttribute("type", strType);
                    Item keyword = getKeyword(strType);
                    feature.addReference(new Reference("feature", keyword.getIdentifier()));
                    if (attrs.getValue("status") != null) {
                        strStatus = attrs.getValue("status");
                        if (strName != null) {
                            strName += " (" + strStatus + ")";
                        } else {
                            strName = strStatus;
                        }
                    }
                    if (strName != null) {
                        feature.setAttribute("description", strName);
                    }
                // <entry><feature><location><start||end>
                } else if ((qName.equals("begin") || qName.equals("end")
                                || qName.equals("position"))
                                && stack.peek().equals("location")
                                && attrs.getValue("position") != null
                                && feature != null) {

                        if (qName.equals("begin") || qName.equals("end")) {
                            feature.setAttribute(qName, attrs.getValue("position"));
                        } else {
                            feature.setAttribute("begin", attrs.getValue("position"));
                            feature.setAttribute("end", attrs.getValue("position"));
                        }
                // <entry><dbreference type="InterPro" >
                } else if (createInterpro
                                && qName.equals("dbReference")
                                && attrs.getValue("type").equals("InterPro")) {
                        String interproId = attrs.getValue("id").toString();
                        String interproItemId = null;
                        if (interproMaster.get(interproId) == null) {
                            interpro = createItem("ProteinFeature");
                            interpro.setAttribute("interproId", interproId);
                            interproItemId = interpro.getIdentifier();
                            interproMaster.put(interproId, interproItemId);
                        } else {
                            interproItemId = (String) interproMaster.get(interproId);
                        }
                        if (interproCollection.getRefIds().isEmpty()) {
                            protein.addCollection(interproCollection);
                        }
                        interproCollection.addRefId(interproItemId);
                // <entry><dbreference type="InterPro"><property type="entry name" value="***"/>
                } else if (createInterpro
                                && qName.equals("property")
                                && attrs.getValue("type").equals("entry name")
                                && stack.peek().equals("dbReference")) {
                        if (interpro != null) {
                            interpro.setAttribute("name", attrs.getValue("value").toString());
                            writer.store(ItemHelper.convert(interpro));
                            interpro = null;
                        }
                // <entry><organism><dbreference>
                } else if (qName.equals("dbReference") && stack.peek().equals("organism")) {
                    taxonId = attrs.getValue("id");
                    Item organism;
                    if (orgMaster.get(taxonId) == null) {
                        organism = createItem("Organism");
                        orgMaster.put(attrs.getValue("id"), organism);
                        organism.setAttribute("taxonId", taxonId);
                        writer.store(ItemHelper.convert(organism));
                    } else {
                        organism = (Item) orgMaster.get(taxonId);
                    }
                    protein.setReference("organism", organism.getIdentifier());
                    // get relevant database for this organism
                    // dbName = (String) taxIdToDb.get(taxonId);
                    UniProtGeneDataMap geneDataMap = (UniProtGeneDataMap) geneDataMaps.get(taxonId);
                    boolean noDatabase = false;
                    if (geneDataMap != null) {
                        dbName = geneDataMap.getSource();                        
                        if (dbName == null) {
                            noDatabase = true;
                        }
                    } else {
                        // there was no data in the config file
                        geneDataMap = new UniProtGeneDataMap("UniProt");
                        noDatabase = true;
                    }
                    if (noDatabase) {
                        geneDataMap.setSource("UniProt"); 
                        String message = "No gene source database defined for organism: "
                            + taxonId + ", using UniProt.[" + geneDataMap.toString()  + "]";
                        LOG.warn(message);
                        dbName = "UniProt";
                    } 
                // <entry><reference><citation><dbreference>
                } else if (hasPrimary  && qName.equals("dbReference")
                           && stack.peek().equals("citation")
                           && attrs.getValue("type").equals("PubMed")) {
                    String pubId;
                    if (pubMaster.get(attrs.getValue("id")) == null) {
                        Item pub = createItem("Publication");
                        pub.setAttribute("pubMedId", attrs.getValue("id"));
                        pubMaster.put(attrs.getValue("id"), pub.getIdentifier());
                        pubId = pub.getIdentifier();
                        writer.store(ItemHelper.convert(pub));
                    } else {
                        pubId = (String) pubMaster.get(attrs.getValue("id"));
                    }
                    // if this is the first publication for this protein, add collection
                    if (pubCollection.getRefIds().isEmpty()) {
                        protein.addCollection(pubCollection);
                    }
                    pubCollection.addRefId(pubId);
                // <entry><comment>
                } else if (qName.equals("comment") && attrs.getValue("type") != null) {
                    comment = createItem("Comment");
                    comment.setAttribute("type", attrs.getValue("type"));
                // <entry><comment><text>
                } else if (qName.equals("text") && stack.peek().equals("comment")) {
                    attName = "text";
                // <entry><keyword>
                } else if (qName.equals("keyword")) {
                    attName = "keyword";
                // <entry><gene>
                } else if (qName.equals("gene")) {
                    initGene();
                // <entry><gene><name>
                } else if (qName.equals("name") && stack.peek().equals("gene")) {
                    // will be primary, ORF, synonym or ordered locus
                    attName = attrs.getValue("type");
                // <dbreference type="EC">
                } else if (qName.equals("dbReference") && attrs.getValue("type").equals("EC")) {
                    String ecNumber = attrs.getValue("id");
                    if (ecNumber != null) {
                        protein.setAttribute("ecNumber", ecNumber);
                    }
                // <dbreference type="FlyBase/UniProt/etc.." id="*" key="12345">
                } else if (qName.equals("dbReference")
                           && geneSources.contains(attrs.getValue("type"))) {
                    // could be identifier but check next tag to see if this is a gene designation
                    possibleGeneId = attrs.getValue("id");
                    possibleGeneIdSource = attrs.getValue("type");
                //    <dbreference><property type="gene designation" value="*">
                } else if (qName.equals("property") && stack.peek().equals("dbReference")
                           && attrs.getValue("type").equals("gene designation")
                           && geneNames.contains(attrs.getValue("value"))) {
                    /* for everyone but homo sapiens */
                    if (possibleGeneIdSource != null && possibleGeneId != null) {
                        geneDesignations.put(possibleGeneIdSource, new String(possibleGeneId));
                    }                    
               //    <dbreference><property type="organism name" value="Homo sapiens"/>
                } else if (qName.equals("property") && stack.peek().equals("dbReference")
                           && attrs.getValue("type").equals("organism name")
                           && attrs.getValue("value").equals("Homo sapiens")) {
                    if ((possibleGeneIdSource != null) && (possibleGeneId != null)
                            && (geneDesignations != null)) {
                        geneDesignations.put(possibleGeneIdSource, new String(possibleGeneId));
                    }
                    
                // <uniprot>
                } else if (qName.equals("uniprot")) {
                   initData();
                }
            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
            super.startElement(uri, localName, qName, attrs);
            stack.push(qName);
            attValue = new StringBuffer();
        }


        /**
         * @see DefaultHandler#endElement
         */
        public void characters(char[] ch, int start, int length) throws SAXException
        {

            if (attName != null) {

                // DefaultHandler may call this method more than once for a single
                // attribute content -> hold text & create attribute in endElement
                while (length > 0) {
                    boolean whitespace = false;
                    switch(ch[start]) {
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
                    ++start;
                    --length;
                }

                if (length > 0) {
                    StringBuffer s = new StringBuffer();
                    s.append(ch, start, length);
                    attValue.append(s);
                }
            }
        }


        /**
         * @see DefaultHandler#endElement
         */
        public void endElement(String uri, String localName, String qName)
            throws SAXException {
            super.endElement(uri, localName, qName);

            try {
                stack.pop();

                // <entry>
                if (qName.equals("entry")) {

                    // only store the protein if it has a primary accession value
                    if (hasPrimary) {

                        // now that we know the taxonID, we can store the genes
                        if (hasPrimary && !genes.isEmpty()) {

                            Iterator i = genes.values().iterator();

                            while (i.hasNext()) {
                                Item gene = (Item) i.next();
                                finaliseGene(gene, protein.getReference("organism").getRefId());
                            }
                        }

                        protein.setAttribute("description", descr.toString());
                        ReferenceList evidenceColl = new ReferenceList("evidence", new ArrayList());
                        protein.addCollection(evidenceColl);
                        evidenceColl.addRefId(datasource.getIdentifier());
                        writer.store(ItemHelper.convert(protein));

                        // <entry><name> is a synonym
                        Item syn = createSynonym(protein.getIdentifier(), "identifier",
                                                 protein.getAttribute("identifier").getValue(),
                                                 datasource.getIdentifier());

                        if (syn != null) {
                            writer.store(ItemHelper.convert(syn));
                        }

                    } else {
                       LOG.info("Entry " + protein.getAttribute("name")
                                + " does not have any accessions");
                    }

                // <entry><sequence>
                } else if (hasPrimary && qName.equals("sequence")) {

                    if (attName != null) {
                        sequence.setAttribute(attName, attValue.toString());
                        protein.addReference(new Reference("sequence", sequence.getIdentifier()));
                        writer.store(ItemHelper.convert(sequence));
                    } else {
                        LOG.info("Sequence for " + protein.getAttribute("name")
                                + " does not have a length");
                    }

                // <entry><protein><name>
                } else if (hasPrimary && qName.equals("name") && stack.peek().equals("protein")) {

                    String proteinName = attValue.toString();

                    if (!protein.hasAttribute("name")) {
                        protein.setAttribute(attName, proteinName);
                        descr.append(proteinName);
                    } else {
                        descr.append(" (" + proteinName + ")");
                    }

                    // all names are synonyms
                    if (evidence != null) {
                        proteinName += " (Evidence " + evidence + ")";
                    }
                    Item syn = createSynonym(protein.getIdentifier(), "name", proteinName,
                                  datasource.getIdentifier());
                    if (syn != null) {
                        writer.store(ItemHelper.convert(syn));
                    }

                // <entry><comment><text>
                } else if (hasPrimary && qName.equals("text") && attName != null) {

                    if (comment.hasAttribute("type") && attValue.toString() != null) {

                        comment.setAttribute(attName, attValue.toString());
                        comment.setReference("source", dataset.getIdentifier());
                        if (commentCollection.getRefIds().isEmpty()) {
                            protein.addCollection(commentCollection);
                        }
                        commentCollection.addRefId(comment.getIdentifier());
                        writer.store(ItemHelper.convert(comment));
                    }

                // <entry><gene><name>
                } else if (qName.equals("name") && stack.peek().equals("gene")) {

                    String type = attName;
                    String name = attValue.toString();

                    // See #1199 - remove organism prefixes ("AgaP_" or "Dmel_")
                    name = name.replaceAll("^[A-Z][a-z][a-z][A-Za-z]_", "");
                    
                    geneNames.add(new String(name));

                    // genes can have more than one synonym, so use name as key for map
                    if (!type.equals("synonym")) {
                        geneNameTypeToName.put(type, name);
                    } else {
                        geneNameTypeToName.put(name, name);
                    }

                // <entry><gene>
                } else if (qName.equals("gene")) {

                    Item gene = createItem("Gene");
                    genes.put(gene.getIdentifier(), gene);

                    // associate gene with lists
                    geneTOgeneNameTypeToName.put(gene.getIdentifier(), geneNameTypeToName);
                    geneTOgeneDesignations.put(gene.getIdentifier(), geneDesignations);

                // <entry><keyword>
                } else if (qName.equals("keyword")) {

                    if (attName != null) {
                        Item keyword = getKeyword(attValue.toString());
                        if (keywordCollection.getRefIds().isEmpty()) {
                            protein.addCollection(keywordCollection);
                        }
                        keywordCollection.addRefId(keyword.getIdentifier());
                    }

                // <entry><feature>
                } else if (qName.equals("feature") && feature != null) {

                    writer.store(ItemHelper.convert(feature));
                    feature = null;

                // <entry><name>
                } else if (qName.equals("name")) {

                    if (attName != null) {
                        protein.setAttribute(attName, attValue.toString());
                    }

                // <entry><accession>
                } else if (qName.equals("accession")) {

                    Item syn = createSynonym(protein.getIdentifier(), "accession",
                                           attValue.toString(), datasource.getIdentifier());
                    if (syn != null) {

                        // if this is the first accession value, its the primary accession
                        if (protein.getAttribute("primaryAccession") == null) {
                            protein.setAttribute("primaryAccession", attValue.toString());
                            hasPrimary = true;
                        }                        
                        if (hasPrimary) {
                            writer.store(ItemHelper.convert(syn));
                        }
                    }
                }

            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
       }

        private void initData()
        throws SAXException    {
            try {
                // TODO: the dataset name shouldn't be hard coded:
                datasource = getDataSource("UniProt");
                dataset = getDataSet("Uniprot data set");
                setOnto("UniProtKeyword");
            } catch (Exception e) {
                throw new SAXException(e);
            }

        }

        // if synonym new, create and put in synonyms map for this <entry>
        private Item createSynonym(String subjectId, String type, String value, String dbId) {
            String key = subjectId + type + value + dbId;
            if (!synonyms.containsKey(key)) {
                Item syn = createItem("Synonym");
                syn.addReference(new Reference("subject", subjectId));
                syn.addAttribute(new Attribute("type", type));
                syn.addAttribute(new Attribute("value", value));
                syn.addReference(new Reference("source", dbId));
                synonyms.put(key, syn);
                return syn;
            } else {
                return null;
            }
        }


        // clears all protein-related lists/values
        // called when new protein is created
        private void initProtein() {

            protein = createItem("Protein");
            featureCollection = new ReferenceList("features", new ArrayList());
            keywordCollection = new ReferenceList("keywords", new ArrayList());
            commentCollection = new ReferenceList("comments", new ArrayList());
            pubCollection = new ReferenceList("publications", new ArrayList());
            interproCollection = new ReferenceList("proteinFeatures", new ArrayList());
            geneCollection = null;

            genes = new HashMap();
            synonyms = new HashMap();
            descr = new StringBuffer();
            taxonId = null;
            dbName = null;
            comment = null;
            feature = null;
            sequence = null;
            hasPrimary = false;

            // maps gene to that gene's lists
            geneTOgeneNameTypeToName = new HashMap();
            geneTOgeneDesignations = new HashMap();
        }


        private void initGene() {

            // list of possible names for this gene
            geneNames = new HashSet();
            // ORF, primary, etc name value for gene
            geneNameTypeToName = new HashMap();
            // gene names from each database
            geneDesignations = new HashMap();

        }

        private Item getDataSource(String title)
            throws SAXException {
            Item database = (Item) dbMaster.get(title);
            try {

                if (database == null) {
                    database = createItem("DataSource");
                    database.addAttribute(new Attribute("name", title));
                    dbMaster.put(title, database);
                    writer.store(ItemHelper.convert(database));
                }

            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
            return database;
        }

        private Item getKeyword(String title)
        throws SAXException {
            Item keyword = (Item) keyMaster.get(title);
            try {

                if (keyword == null) {
                    keyword = createItem("OntologyTerm");
                    keyword.addAttribute(new Attribute("name", title));
                    Item ontology = (Item) ontoMaster.get("UniProtKeyword");
                    keyword.addReference(new Reference("ontology", ontology.getIdentifier()));
                    keyMaster.put(title, keyword);
                    writer.store(ItemHelper.convert(keyword));
                }

            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
            return keyword;
        }

        private Item getDataSet(String title)
            throws SAXException {
            Item ds = (Item) dsMaster.get(title);
            try {

                if (ds == null) {
                    ds = createItem("DataSet");
                    ds.addAttribute(new Attribute("title", title));
                    dsMaster.put(title, ds);
                    writer.store(ItemHelper.convert(ds));
                }

            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
            return ds;
        }

        private Item setOnto(String title)
        throws SAXException {

            Item ontology = (Item) ontoMaster.get(title);
            try {
                if (ontology == null) {
                    ontology = createItem("Ontology");
                    ontology.addAttribute(new Attribute("title", title));
                    ontoMaster.put(title, ontology);
                    writer.store(ItemHelper.convert(ontology));
                }

            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
            return ontology;
        }

        private void finaliseGene(Item gene, String orgId)
            throws SAXException {
            try {

                // Gene.identifier = <entry><gene><name type="ORF">
                String geneIdentifier = null;
                // Gene.name = <entry><gene><name type="primary">
                String primaryGeneName = null;

                // get list for this gene
                HashMap nameTypeToName = (HashMap)
                    geneTOgeneNameTypeToName.get(gene.getIdentifier());
                HashMap designations = (HashMap)
                    geneTOgeneDesignations.get(gene.getIdentifier());

                // loop through each name for this gene
                String notCG = null;
                Iterator i = nameTypeToName.keySet().iterator();
                while (i.hasNext()) {

                    String type = (String) i.next();
                    String name = (String) nameTypeToName.get(type);

                    if (type.equals("primary")) {
                        primaryGeneName = name;
                    } else if (type.equals("ORF")) {
                        if (taxonId.equals("7227") && !name.startsWith("CG")) {
                            notCG = name;
                        } else {
                            geneIdentifier = name;
                        }
                    }
                }

                // Some UniProt entries have CGxxx as Dmel_CGxxx - need to strip prefix
                // so that they match identifiers from other sources.  Some genes have
                // embl identifiers and no FlyBase id, ignore these.
                if (geneIdentifier == null && notCG != null) {
                    if (notCG.startsWith("Dmel_")) {
                        geneIdentifier = notCG.substring(5);
                    } else {
                        LOG.info("Found a Drosophila gene without a CG identifer: " + notCG);
                    }
                }
                
                
                // define a gene identifier we always expect to find that is unique to this gene
                // is different for each organism
                String uniqueGeneIdentifier = null;
                // geneOrganismDbId = <entry><dbReference><type="FlyBase/WormBase/..">
                //            where designation = primary gene name
                String geneOrganismDbId = null;
                
                // use map to find out where to get ids
                UniProtGeneDataMap geneDataMap = (UniProtGeneDataMap) geneDataMaps.get(taxonId);
                
                if (geneDataMap != null) {
                   
                    /* set vars if they come from datasource or name */
                    geneOrganismDbId = setGeneVars(designations, nameTypeToName, null,
                                                 geneDataMap.getOrganismDbIdSrcType(),
                                                 geneDataMap.getOrganismDbIdSrc(),
                                                 geneOrganismDbId);
    
                    geneIdentifier = setGeneVars(designations, nameTypeToName, null,
                                                   geneDataMap.getIdentifierSrcType(),
                                                   geneDataMap.getIdentifierSrc(),
                                                   geneIdentifier);
                    
                    /* set vars if they come from another variable */
                    Map variableLookup = new HashMap();
                    variableLookup.put("geneIdentifier", geneIdentifier);
                    variableLookup.put("geneOrganismDbId", geneOrganismDbId);
                    variableLookup.put("primaryGeneName", primaryGeneName);
                    
                    geneOrganismDbId = setGeneVars(null, null, variableLookup, 
                                                   geneDataMap.getOrganismDbIdSrcType(),
                                                   geneDataMap.getOrganismDbIdSrc(),
                                                   geneOrganismDbId);
    
                    geneIdentifier = setGeneVars(null, null, variableLookup, 
                                                 geneDataMap.getIdentifierSrcType(),
                                                 geneDataMap.getIdentifierSrc(),
                                                 geneIdentifier);
                    
                    /* organism specific */
                    if (taxonId.equals("10116")) { // Rattus norvegicus
                        if (geneOrganismDbId != null && !geneOrganismDbId.startsWith("RGD:")) {
                            geneOrganismDbId = "RGD:" + geneOrganismDbId;
                        }
                    } else if (taxonId.equals("3702")) { // Arabidopsis thaliana                 
                        if (geneOrganismDbId != null) {
                            geneOrganismDbId = geneOrganismDbId.toUpperCase();
                        }
                    }
                
                    variableLookup = new HashMap();
                    variableLookup.put("geneIdentifier", geneIdentifier);
                    variableLookup.put("geneOrganismDbId", geneOrganismDbId);
                    variableLookup.put("primaryGeneName", primaryGeneName);   
                    
                    /* set unique identifier */
                    uniqueGeneIdentifier = (String) variableLookup.get(geneDataMap.getAttribute());
                    variableLookup = null;
                }
                
                // uniprot data source has primary key of Gene.organismDbId
                // only create gene if a value was found
                if (uniqueGeneIdentifier != null) {
                    String geneItemId = (String) geneMaster.get(uniqueGeneIdentifier);

                    // UniProt sometimes has same identifier paired with two organismDbIds
                    // causes problems merging other data sources.  Simple check to prevent
                    // creating a gene with a duplicate identifier.

                    // log problem genes
                    boolean isDuplicateIdentifier = false;
                    if ((geneItemId == null) && geneIdentifiers.contains(geneIdentifier)) {
                        LOG.warn("already created a gene for identifier: " + geneIdentifier
                                 + " with different organismDbId, discarding this one");
                        isDuplicateIdentifier = true;
                    }
                    if ((geneItemId == null) && !isDuplicateIdentifier) {

                        if (geneOrganismDbId != null) {
                            if (geneOrganismDbId.equals("")) {
                                LOG.info("geneOrganismDbId was empty string");
                            }
                            gene.addAttribute(new Attribute("organismDbId", geneOrganismDbId));

                            Item syn = createSynonym(gene.getIdentifier(), "identifier",
                                          geneOrganismDbId,
                                          getDataSource(dbName).getIdentifier());
                            if (syn != null) {
                                writer.store(ItemHelper.convert(syn));
                            }
                        }
                        if (geneIdentifier != null) {
                            gene.addAttribute(new Attribute("identifier", geneIdentifier));
                            // don't create duplicate synonym
                            if (!geneIdentifier.equals(geneOrganismDbId)) {

                                Item syn = createSynonym(gene.getIdentifier(), "identifier",
                                           geneIdentifier,
                                           getDataSource(dbName).getIdentifier());
                                if (syn != null) {
                                    writer.store(ItemHelper.convert(syn));
                                }
                            }
                            // keep a track of non-null gene identifiers
                            geneIdentifiers.add(geneIdentifier);
                        }
                        // Problem with gene names for drosophila - ignore
                        if (primaryGeneName != null && !taxonId.equals("7227")) {
                            gene.addAttribute(new Attribute("symbol", primaryGeneName));
                        }
                        if (geneCollection == null) {
                            geneCollection = new ReferenceList("genes", new ArrayList());
                            protein.addCollection(geneCollection);
                        }
                        geneMaster.put(uniqueGeneIdentifier, gene.getIdentifier());
                        geneCollection.addRefId(gene.getIdentifier());
                        gene.setReference("organism", orgId);
                        ReferenceList evidenceColl = new ReferenceList("evidence", new ArrayList());
                        gene.addCollection(evidenceColl);
                        evidenceColl.addRefId(datasource.getIdentifier());
                        writer.store(ItemHelper.convert(gene));
                        i = nameTypeToName.keySet().iterator();
                        while (i.hasNext()) {

                            String synonymDescr = "";
                            String type = (String) i.next();
                            String name = (String) nameTypeToName.get(type);

                            if (type.equals("ordered locus")) {
                                synonymDescr = "ordered locus";
                            } else {
                                synonymDescr =  "symbol";
                            }

                            // all gene names are synonyms
                            // ORF is already identifer, so skip
                            if (!type.equals("ORF")) {
                                Item syn = createSynonym(gene.getIdentifier(), synonymDescr, name,
                                              getDataSource(dbName).getIdentifier());
                                if (syn != null) {
                                    writer.store(ItemHelper.convert(syn));
                                }
                            }
                        }
                    }
                }
            } catch (ObjectStoreException e) {
                throw new SAXException(e);
            }
        }

        private String setGeneVars(Map designations, Map nameTypeToName, Map variableLookup, 
                                   String srcType, String src, String var) {  
            if (srcType == null) {
                return var;
            }
            if (srcType.equals("datasource") && designations != null) {
                return (String) designations.get(src);
            } else if (srcType.equals("name") && nameTypeToName != null) {
                return (String) nameTypeToName.get(src);
            } else if (srcType.equals("variable") && variableLookup != null) {
                if (src.equals("NULL")) {
                    return null;
                }
                return (String) variableLookup.get(src);
            }
            return var;            
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
         * Uniquely alias a className
         * @param className the class name
         * @return the alias
         */
        protected String alias(String className) {
            String alias = (String) aliases.get(className);
            if (alias != null) {
                return alias;
            }
            String nextIndex = "" + (nextClsId++);
            aliases.put(className, nextIndex);
            return nextIndex;
        }
    }
        /**
         * Which datasource to use with which organism
         * @author Julie Sullivan
         */
        public static class UniProtGeneDataMap
        {    
            private String attribute;
            private String source;
            private String organismDbIdSrc;
            private String organismDbIdSrcType;
            private String identifierSrcType;
            private String identifierSrc;
            
            /**
             * Constructor
             * @param attribute Which variable to use for the gene's unique identifier
             */
            public UniProtGeneDataMap(String attribute) {
                this.attribute = attribute;
            }
            
            /**
             * What to use as the uniqueGeneIdentifier
             * e.g. geneOrganismDbId or geneIdentifier
             * @return What to use as the uniqueGeneIdentifier
             */        
            public String getAttribute () {
                return attribute;
            }
            
            /**
             * @param source Sources appear as source for gene name synonym and 
             * are used for links out from the webapp
             */
            public void setSource (String source) {
                this.source = source;
            }
            
            /**
             * @return Sources appear as source for gene name synonym and are used for links out 
             * from the webapp
             */
            public String getSource () {
                return source;
            }
                    
            /**
             * @param organismDbIdSrcType What kind of source to use, e.g. variable, datasource, or
             * name
             * @param organismDbIdSrc What source to use, e.g. WormBase or ORF
             */
            public void  setOrganismDbId(String organismDbIdSrcType, String organismDbIdSrc) {
                this.organismDbIdSrcType = organismDbIdSrcType;
                this.organismDbIdSrc = organismDbIdSrc;
            }
            
            /**
             * @return What type of source to use to set geneOrganismDbId, 
             * e.g. variable, datasource, or name
             */
            public String getOrganismDbIdSrcType() {
                return organismDbIdSrcType;
            }
            
            /**
             * @return What source to use to set geneOrganismDbId, e.g. WormBase, ORF, etc
             */
            public String getOrganismDbIdSrc() {
                return organismDbIdSrc;
            }
            
            /**
             * @param identifierSrcType What kind of source to use, e.g. variable or datasource
             * @param identifierSrc Which source to use, e.g. geneOrganismDbId, Ensembl
             */
            public void  setIdentifier(String identifierSrcType, String identifierSrc) {
                this.identifierSrcType = identifierSrcType;
                this.identifierSrc = identifierSrc;
            }

            /**
             * @return What kind of source to use to set geneIdentifier, e.g. variable or datasource
             */
            public String getIdentifierSrcType() {
                return identifierSrcType;
            }
            
            /**
             * @return Which source to use to set geneIdentifier, e.g. geneOrganismDbId, Ensembl
             */
            public String getIdentifierSrc() {
                return identifierSrc;
            }
            
            public String toString() {
                return "attribute: " + attribute 
                + ", source: " + source
                + ", organismDbIdSrcType: " + organismDbIdSrcType
                + ", organismDbIdSrc: " + organismDbIdSrc
                + ", identifierSrcType: " + identifierSrcType
                + ", identifierSrc: " + identifierSrc;

            }
        }
    
}
