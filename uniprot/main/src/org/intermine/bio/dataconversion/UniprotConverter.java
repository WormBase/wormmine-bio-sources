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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.intermine.dataconversion.FileConverter;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.SAXParser;
import org.intermine.xml.full.Attribute;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.ItemFactory;
import org.intermine.xml.full.ItemHelper;
import org.intermine.xml.full.Reference;
import org.intermine.xml.full.ReferenceList;

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
    private static final Logger LOG = Logger.getLogger(UniprotConverter.class);
    private Map pubMaster = new HashMap();
    private Map orgMaster = new HashMap();
    private Map dbMaster = new HashMap();
    private Map dsMaster = new HashMap();
    private Map taxIdToDb = new HashMap();
    private Map geneMaster = new HashMap();
    private Set geneIdentifiers = new HashSet();
    private Map ids = new HashMap();
    private Map aliases = new HashMap();
    
    
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

        mapMaps();
        mapDatabases();
        UniprotHandler handler = new UniprotHandler(writer, mapMaster);
       
        try {
            SAXParser.parse(new InputSource(reader), handler);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void mapMaps() {
        
        mapMaster.put("pubMaster", pubMaster);
        mapMaster.put("orgMaster", orgMaster);
        mapMaster.put("dbMaster", dbMaster);
        mapMaster.put("taxIdToDb", taxIdToDb);
        mapMaster.put("dsMaster", dsMaster);
        mapMaster.put("geneMaster", geneMaster);
        mapMaster.put("geneIdentifiers", geneIdentifiers);
        mapMaster.put("ids", ids);
        mapMaster.put("aliases", aliases);
    }
    
    // makes map so we know which datasource to use for each organism
    private void mapDatabases() {

        //  this map is used to check if the value here should be saved:
        //   <dbReference type="FlyBase/UniProt/etc" id="*" key="12345">
        taxIdToDb.put("7227", new String("FlyBase"));   // D. melanogaster
        taxIdToDb.put("6239", new String("WormBase"));  // C. elegans
        taxIdToDb.put("3702", new String("UniProt"));   // Arabidopsis thaliana
        taxIdToDb.put("4896", new String("GeneDB"));    // S. pombe
        taxIdToDb.put("180454", new String("Ensembl")); // A. gambiae str. PEST
        taxIdToDb.put("7165", new String("Ensembl"));   // Anopheles gambiae
        taxIdToDb.put("7460", new String("Ensembl"));   // Apis mellifera
        taxIdToDb.put("9606", new String("Ensembl"));   // H. sapiens
        taxIdToDb.put("4932", new String("SGD"));       // S. cerevisiae
        taxIdToDb.put("36329", new String("GeneDB"));   // Malaria
        taxIdToDb.put("10090", new String("Ensembl"));  // Mus musculus
        taxIdToDb.put("10116", new String("Ensembl"));  // Rattus norvegicus


        // a. these databases are used to obtain the geneOrganismDbId
        //    (instead of data source)
        // b. dummy taxonIDs are used
        // c. these are here because this map is used as a look up
        //    to see if the value in the dbreference's id field is an identifier
        taxIdToDb.put("-1", new String("SGD")); // S. cerevisiae [geneOrganismDbId]
        taxIdToDb.put("-2", new String("MGI")); // Mus musculus [geneOrganismDbId]
        taxIdToDb.put("-3", new String("RGD")); // Rattus norvegicus [geneOrganismDbId]
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
        //private Item synonym;
        private Map synonyms;
        private Map genes;
        private StringBuffer descr;
        private String taxonId;
        private String dbName;
        private String evidence;
        private boolean hasPrimary;

        private ReferenceList pubCollection;
        private ReferenceList commentCollection;
        private ReferenceList geneCollection;

        // maps genes for this protein to that gene's lists of names, identifiers, etc
        private Map gene_to_geneNameTypeToName;        
        private Map gene_to_geneDesignations;

        // reset for each gene
        private Map geneNameTypeToName;     // ORF, primary, etc value for gene name
        private Set geneNames;              // list of names for this gene
        private Map geneDesignations;       // gene names from each database      
        private String possibleGeneIdSource;// ie FlyBase, Ensemble, etc. 
        private String possibleGeneId;      // temp holder for gene identifier 
                                            // until "gene designation" is verified on next line

        // master lists - only one is created
        private Map pubMaster;
        private Map orgMaster;
        private Map dbMaster;
        private Map dsMaster;
        private Map taxIdToDb;        // which database to use for which organism
        private Item datasource;             
        private Item dataset;          
        private Map geneMaster;       // itemID to gene
        private Set geneIdentifiers;  // all gene identifiers
        private Map ids;
        private Map aliases;
        
        private ItemWriter writer;       
        
        private Stack stack = new Stack();
        private String attName = null;
        private StringBuffer attValue = null;

        /**
         * Constructor
         * @param writer the ItemWriter used to handle the resultant items
         * @param mapMaster the Map of maps
         */
        public UniprotHandler(ItemWriter writer, Map mapMaster) {
            
            itemFactory = new ItemFactory(Model.getInstanceByName("genomic"));
            this.writer = writer;
            
            this.pubMaster = (Map) mapMaster.get("pubMaster");
            this.orgMaster = (Map) mapMaster.get("orgMaster");
            this.dbMaster = (Map) mapMaster.get("dbMaster");
            this.dsMaster = (Map) mapMaster.get("dsMaster");
            this.taxIdToDb = (Map) mapMaster.get("taxIdToDb");
            this.geneMaster = (Map) mapMaster.get("geneMaster");
            this.geneIdentifiers = (Set) mapMaster.get("geneIdentifiers");
            this.ids = (Map) mapMaster.get("ids");
            this.aliases = (Map) mapMaster.get("aliases");
        }

        
        /**
         * @see DefaultHandler#startElement
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

                // <entry><organism><dbreference>
                } else if (qName.equals("dbReference") && stack.peek().equals("organism")) {

                    taxonId = attrs.getValue("id");
                    Item organism;

                    // if organism isn't in master list, add
                    // otherwise, just get the id from the master list
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
                    dbName = (String) taxIdToDb.get(taxonId);
                   
                    // now that we know the taxonID, we can store the genes
                    if (hasPrimary && !genes.isEmpty()) {

                        Iterator i = genes.values().iterator();

                        while (i.hasNext()) {
                            Item gene = (Item) i.next();
                            finaliseGene(gene, organism.getIdentifier());
                        }
                    }

                // <entry><reference><citation><dbreference>
                } else if (hasPrimary  && qName.equals("dbReference")
                           && stack.peek().equals("citation")
                           && attrs.getValue("type").equals("PubMed")) {

                    String pubId;

                    // if publication isn't in master list, add it
                    // otherwise, just get the id from the master list
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
                           && taxIdToDb.containsValue(attrs.getValue("type"))) {

                    // could be identifiers but check next tag to see if this is a gene designation
                    possibleGeneId = attrs.getValue("id");
                    possibleGeneIdSource = attrs.getValue("type");


                // <dbreference><property type="gene designation" value="*">
                } else if (qName.equals("property") && stack.peek().equals("dbReference")
                           && attrs.getValue("type").equals("gene designation")
                           && geneNames.contains(attrs.getValue("value"))) {
                    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    // need to handle when there is no property
                    // or there is only one name
                    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    // Ensemble?
                    if (possibleGeneIdSource != null && possibleGeneId != null)
                        geneDesignations.put(possibleGeneIdSource, new String(possibleGeneId)); 

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

                        protein.setAttribute("description", descr.toString());
                        ReferenceList evidenceColl = new ReferenceList("evidence", new ArrayList());
                        protein.addCollection(evidenceColl);
                        evidenceColl.addRefId(datasource.getIdentifier());
                        writer.store(ItemHelper.convert(protein));
                                               
                        // <entry><name> is a synonym
                        Item syn = createSynonym(protein.getIdentifier(), "identifier", 
                                                 protein.getAttribute("identifier").getValue(),
                                                 datasource.getIdentifier());

                        if (syn != null)
                            writer.store(ItemHelper.convert(syn));                    

                    } else {
                       LOG.info("Entry " + protein.getAttribute("name")
                                + " does not have any accessions");
                    }

                // <entry><sequence>
                } else if (hasPrimary && qName.equals("sequence")) {

                    if (attName != null) {
                        sequence.setAttribute(attName, attValue.toString());
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
                    if (syn != null)
                        writer.store(ItemHelper.convert(syn));
                    
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

                    geneNames.add(new String(name));
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
                    gene_to_geneNameTypeToName.put(gene.getIdentifier(), geneNameTypeToName);
                    gene_to_geneDesignations.put(gene.getIdentifier(), geneDesignations);

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

            commentCollection = new ReferenceList("comments", new ArrayList());
            pubCollection = new ReferenceList("publications", new ArrayList());
            geneCollection = null;

            genes = new HashMap(); 
            synonyms = new HashMap(); 
            descr = new StringBuffer();
            taxonId = null;
            //synonym = null;
            dbName = null;
            comment = null;
            sequence = null;
            hasPrimary = false;

            // maps gene to that gene's lists
            gene_to_geneNameTypeToName = new HashMap(); 
            gene_to_geneDesignations = new HashMap();
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
        
        private void finaliseGene(Item gene, String orgId)
            throws SAXException {
            try {

                // Gene.identifier = <entry><gene><name type="ORF">
                String geneIdentifier = null;
                // Gene.name = <entry><gene><name type="primary">
                String primaryGeneName = null;

                // get list for this gene
                HashMap nameTypeToName = (HashMap) gene_to_geneNameTypeToName.get(gene.getIdentifier());
                HashMap designations = (HashMap) gene_to_geneDesignations.get(gene.getIdentifier());
       
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

                if (taxonId.equals("7227")) { // D. melanogaster
                    // UniProt has duplicate pairings of CGxxx and FBgnxxx, just get one id
                    geneOrganismDbId = null;
                    uniqueGeneIdentifier = geneIdentifier;

                } else if (taxonId.equals("6239")) { // C. elegans
                    // just get WBGeneXXX - ORF id is a gene *model* id,
                    // i.e. effectively a transcript
                    //geneOrganismDbId
                    //           = getDataSourceReferenceValue(srcItem, "WormBase", geneNames);
                    geneOrganismDbId = (String) designations.get("WormBase");
                    uniqueGeneIdentifier = geneOrganismDbId;
                    geneIdentifier = null;

                } else if (taxonId.equals("3702")) { // Arabidopsis thaliana
                    
                    // may not have ordered locus?  what to do then?
                    geneOrganismDbId = (String) nameTypeToName.get("ordered locus");
                    if (geneOrganismDbId != null) {
                        geneOrganismDbId = geneOrganismDbId.toUpperCase();
                        uniqueGeneIdentifier = geneOrganismDbId;
                    }

                } else if (taxonId.equals("4896")) {  // S. pombe
                    geneOrganismDbId = (String) nameTypeToName.get("ORF");
                    uniqueGeneIdentifier = geneOrganismDbId;

                } else if (taxonId.equals("180454")) { // A. gambiae str. PEST
                    // no organismDbId and no specific dbxref to ensembl - assume that
                    // geneIdentifier is always ensembl gene stable id and set organismDbId
                    // to be identifier
                    uniqueGeneIdentifier = geneIdentifier;
                    geneOrganismDbId = geneIdentifier;
                
                    // ~~~ put back once test data is verified? ~~~
               //} else if (taxonId.equals("7165") || taxonId.equals("7460")) { // bees
                    // Richard told me to do this.
                    // uniqueGeneIdentifier = geneIdentifier;
                    // geneOrganismDbId = geneIdentifier;
                                        
                } else if (taxonId.equals("9606")) { // H. sapiens
                    //geneOrganismDbId = getDataSourceReferenceValue(srcItem, "Ensembl", null);
                    geneOrganismDbId = (String) designations.get("Ensembl");
                    geneIdentifier = geneOrganismDbId;
                    uniqueGeneIdentifier = geneOrganismDbId;


                } else if (taxonId.equals("4932")) { // S. cerevisiae
                    // need to set SGD identifier to be SGD accession, also set organismDbId
                    geneIdentifier = (String) designations.get("Ensembl");
                    geneOrganismDbId = (String) designations.get("SGD");
                    uniqueGeneIdentifier = geneOrganismDbId;

                } else if (taxonId.equals("36329")) { // Malaria
                    geneOrganismDbId = geneIdentifier;
                    uniqueGeneIdentifier = geneIdentifier;

                } else if (taxonId.equals("10090")) { // Mus musculus

                    geneIdentifier = (String) designations.get("Ensembl");
                    geneOrganismDbId = (String) designations.get("MGI");
                    uniqueGeneIdentifier = geneOrganismDbId;

                } else if (taxonId.equals("10116")) { // Rattus norvegicus

                    geneIdentifier = (String) designations.get("Ensembl");
                    geneOrganismDbId = (String) designations.get("RGD");

                    // HACK in other places the RGD identifers start with 'RGD:'
                    if (geneOrganismDbId != null && !geneOrganismDbId.startsWith("RGD:")) {
                        geneOrganismDbId = "RGD:" + geneOrganismDbId;
                    }
                    uniqueGeneIdentifier = geneOrganismDbId;
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
                            if (syn != null)
                                writer.store(ItemHelper.convert(syn));
                        }

                        if (geneIdentifier != null) {
                            gene.addAttribute(new Attribute("identifier", geneIdentifier));
                            // don't create duplicate synonym
                            if (!geneIdentifier.equals(geneOrganismDbId)) {

                                Item syn = createSynonym(gene.getIdentifier(), "identifier", 
                                           geneIdentifier,
                                           getDataSource(dbName).getIdentifier());
                                if (syn != null)
                                    writer.store(ItemHelper.convert(syn));
                            }
                            // keep a track of non-null gene identifiers
                            geneIdentifiers.add(geneIdentifier);

                        }
                        // Problem with gene names for drosophila - ignore
                        if (primaryGeneName != null && !taxonId.equals("7227")) {
                            gene.addAttribute(new Attribute("symbol", primaryGeneName));
                        }
                        
                        //geneMaster.put(geneIdentifier, gene.getIdentifier());
                        geneMaster.put(uniqueGeneIdentifier, gene.getIdentifier());
                        
                        if (geneCollection == null) {
                            geneCollection = new ReferenceList("genes", new ArrayList());
                            protein.addCollection(geneCollection);
                        }
                        
                        geneCollection.addRefId(gene.getIdentifier());
                        gene.setAttribute("identifier", uniqueGeneIdentifier);
                                                
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
                                if (syn != null)
                                    writer.store(ItemHelper.convert(syn));                                
                            }
                        }
                    }
                }
            } catch (ObjectStoreException e) {
                throw new SAXException(e);
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


}

