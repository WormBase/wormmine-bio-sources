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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.intermine.dataconversion.FileConverter;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.TextFileUtil;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.ReferenceList;

import java.io.Reader;

import org.apache.log4j.Logger;

/**
 * DataConverter to parse BDGP insitu data file into Items.
 *
 * @author Julie Sullivan
 */
public class BDGPInsituConverter extends FileConverter
{
    protected static final Logger LOG = Logger.getLogger(BDGPInsituConverter.class);

    private static final String URL 
    = "http://www.fruitfly.org/insituimages/insitu_images/thumbnails/";
    private Map<String, Item> genes = new HashMap<String, Item>();
    private Map<String, Item> terms = new HashMap<String, Item>();
    private Map<String, Item> results = new HashMap<String, Item>();
    private Map<String, Item> imgs = new HashMap<String, Item>();
    
    Item orgDrosophila;
    private Item dataSet;
    private Item pub;
    private String[] stages;
    private String[] stageDescriptions;
    private Set<String> badTerms;
    
    /**
     * Construct a new instance of BDGPInsituConverter.
     *
     * @param model the Model
     * @param writer the ItemWriter used to handle the resultant items
     * @throws ObjectStoreException if an error occurs in storing
     */
    public BDGPInsituConverter(ItemWriter writer, Model model) throws ObjectStoreException {
        super(writer, model);

        orgDrosophila = createItem("Organism");
        orgDrosophila.setAttribute("taxonId", "7227");
        store(orgDrosophila);
        
        Item dataSource = createItem("DataSource");
        dataSource.setAttribute("name", "BDGP");
        dataSource.setAttribute("url", "http://www.fruitfly.org");
        store(dataSource);

        // the widget depends on this name
        dataSet = createItem("DataSet");
        dataSet.setAttribute("title", "BDGP in situ data set");
        dataSet.setReference("dataSource", dataSource.getIdentifier());
        store(dataSet);

        pub = createItem("Publication");
        pub.setAttribute("pubMedId", "17645804");
        store(pub);
        
        stages = getStages();
        stageDescriptions = getStageDescriptions();
        badTerms = getBadTerms();
    }

    /**
     * Process the results matrix from Fly-FISH.
     * @param reader the Reader
     * @see DataConverter#process
     * @throws Exception if something goes wrong
     */
    @Override
    public void process(Reader reader) throws Exception {

        Iterator<String[]> it = TextFileUtil.parseCsvDelimitedReader(reader);

        while (it.hasNext()) {

            String lineBits[] = it.next();
            String geneCG = lineBits[0];
            
            
            
            if (!geneCG.startsWith("CG")) {
                // ignore clones for now
                continue;
            }
            
            Item gene = getGene(geneCG);
            
            String stage = lineBits[1];
                        
            String resultKey = geneCG + stage;
            Item result = getResult(resultKey, gene.getIdentifier(), pub.getIdentifier(), 
                                    dataSet.getIdentifier(), stage);

            if (lineBits.length > 2) {
                String image = lineBits[2];
                Item img = getImage(URL + image);
                result.addToCollection("images", img);
                LOG.error("Could not process line:  " + lineBits[0] + "," + lineBits[1]);
            }
            if (lineBits.length > 3) {
                String term = lineBits[3];
                Item termItem = getTerm(term);
                if (termItem != null) {
                    result.addToCollection("mRNAExpressionTerms", termItem);
                }
                if (term.equalsIgnoreCase("no staining")) {
                    result.setAttribute("expressed", "false");
                }
            }
        }
        
        storeAll(imgs);
        storeAll(results);
        
    }

    private void storeAll(Map<String, Item> map) throws ObjectStoreException {
        for (Item item: map.values()) {
            store(item);
        }
    }
    
    private Item getResult(String key, String geneId, String pubId, String dataSetId, String stage) 
    {
        if (results.containsKey(key)) {
            return results.get(key);
        } else {
            Item result = createItem("MRNAExpressionResult");

            result.setAttribute("expressed", "true");
            
            result.setReference("gene", geneId);
            result.setReference("publication", pubId);
            result.setReference("source", dataSetId);

            setTheStage(result, stage);
                        
            ReferenceList imgColl = new ReferenceList("images", new ArrayList<String>());
            result.addCollection(imgColl);
            ReferenceList termColl = new ReferenceList("mRNAExpressionTerms", 
                                                       new ArrayList<String>());
            result.addCollection(termColl);   
            results.put(key, result);
            
            return result;
        }
    }
    
    private void setTheStage(Item result, String stage) {
        
        ReferenceList stagesColl = new ReferenceList("stages", new ArrayList<String>());

        Integer stageNumber = null;
        try {
            stageNumber = new Integer(stage);
        } catch (NumberFormatException e) {
            // bad line in file, just keep going
            return;
        }

        result.setAttribute("stageRange", stageDescriptions[stageNumber.intValue()] 
                                                                  + " (BDGP in situ)");
        switch (stageNumber.intValue()) {
        case 1:
            stagesColl.addRefId(stages[1]);
            stagesColl.addRefId(stages[2]);
            stagesColl.addRefId(stages[3]);
            break;
        case 2:
            stagesColl.addRefId(stages[4]);
            stagesColl.addRefId(stages[5]);
            stagesColl.addRefId(stages[6]);
            break;                
        case 3:
            stagesColl.addRefId(stages[7]);
            stagesColl.addRefId(stages[8]);
            break;
        case 4:
            stagesColl.addRefId(stages[9]);
            stagesColl.addRefId(stages[10]);
            break;
        case 5:
            stagesColl.addRefId(stages[11]);
            stagesColl.addRefId(stages[12]);
            break;
        case 6:
            stagesColl.addRefId(stages[13]);
            stagesColl.addRefId(stages[14]);
            stagesColl.addRefId(stages[15]);
            stagesColl.addRefId(stages[16]);
            break;               
        }

        result.addCollection(stagesColl);
    }
    
    private Item getTerm(String name) throws ObjectStoreException {
        if (badTerms.contains(name)) {
            return null;
        } else if (terms.containsKey(name)) {
            return terms.get(name);
        } else {
            Item termItem = createItem("MRNAExpressionTerm");
            termItem.setAttribute("name", name);
            termItem.setAttribute("type", "ImaGO");
            store(termItem);
            terms.put(name, termItem);
            return termItem;
        }
    }

    private Item getGene(String geneCG) throws ObjectStoreException {
        if (genes.containsKey(geneCG)) {
            return genes.get(geneCG);
        } else {
            Item gene = createItem("Gene");
            
            String identifierType = "secondaryIdentifier";
            
            if (geneCG.startsWith("FBgn")) {
                identifierType = "primaryIdentifier";
            } else if (geneCG.startsWith("Dl") || geneCG.startsWith("dac")) {
                identifierType = "symbol";
            }
            
            gene.setAttribute(identifierType, geneCG);
            gene.setReference("organism", orgDrosophila);
            genes.put(geneCG, gene);
            store(gene);
            return gene;
        }
    }
    
    private Item getImage(String img) {
        if (imgs.containsKey(img)) {
            return imgs.get(img);
        } else {
            Item item = createItem("Image");
            item.setAttribute("url", img);
            imgs.put(img, item);            
                    
            return item;
        }
    }
    
    private String[] getStageDescriptions() {
        String[] stageLabels = new String[7];
        stageLabels[0] = "";
        stageLabels[1] = "stage 1-3";
        stageLabels[2] = "stage 4-6";
        stageLabels[3] = "stage 7-8";
        stageLabels[4] = "stage 9-10";
        stageLabels[5] = "stage 11-12";
        stageLabels[6] = "stage 13-16";
        return stageLabels;
    }
    
    private String[] getStages() throws ObjectStoreException {
        String[] stageItems = new String[17];
        for (int i = 1; i <= 16; i++) {
            Item stage = createItem("Stage");
            stage.setAttribute("name", "Stage " + i);
            stageItems[i] = stage.getIdentifier();
            store(stage);
        }
        return stageItems;
    }
    
    private Set<String> getBadTerms() {
        Set<String> forbiddenTerms = new HashSet<String>();
        forbiddenTerms.add("does_not_fit_array");
        forbiddenTerms.add("epi_combo");
        forbiddenTerms.add("flag_as_conflicting");
        forbiddenTerms.add("flag_as_incompleto");
        forbiddenTerms.add("flag_as_nonspecific");
        forbiddenTerms.add("flag_for_volker");
        forbiddenTerms.add("go_term");
        return forbiddenTerms;
    }
}

