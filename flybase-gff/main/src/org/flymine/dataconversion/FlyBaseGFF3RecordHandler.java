package org.flymine.dataconversion;

/*
 * Copyright (C) 2002-2005 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;

import org.intermine.metadata.Model;
import org.intermine.xml.full.Attribute;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.ReferenceList;
import org.intermine.util.XmlUtil;

import org.intermine.bio.io.gff3.GFF3Record;

import org.apache.log4j.Logger;

/**
 * A converter/retriever for FlyBase GFF3 files.
 *
 * @author Richard Smith
 */

public class FlyBaseGFF3RecordHandler extends GFF3RecordHandler
{
    Map references;
    private static final Logger LOG = Logger.getLogger(FlyBaseGFF3RecordHandler.class);
    private String tgtNs;
    private Set pseudogeneIds = new HashSet();
    private Map otherOrganismItems = new HashMap();

    // items that need extra processing that can only be done after all other GFF features have
    // been read
    private Collection finalItems = new ArrayList();

    /**
     * Create a new FlyBaseGFF3RecordHandler for the given target model.
     * @param tgtModel the model for which items will be created
     */
    public FlyBaseGFF3RecordHandler(Model tgtModel) {
        super(tgtModel);
        tgtNs = tgtModel.getNameSpace().toString();

        // create a map of classname to reference name for parent references
        // this will add the parents of any SimpleRelations from getParents() to the
        // given collection
        references = new HashMap();
        references.put("Enhancer", "gene");
        references.put("Exon", "transcripts");
        references.put("InsertionSite", "genes");
        references.put("Intron", "transcripts");
        // release 4.0 gff3 is inconsistent with parents of RNAs
        // Gene.transcripts collection set in post-processing
        references.put("MRNA", "gene");
        references.put("NcRNA", "gene");
        references.put("SnRNA", "gene");
        references.put("SnoRNA", "gene");
        references.put("TRNA", "gene");
        references.put("PointMutation", "genes");
        references.put("PolyASite", "processedTranscripts");
        // Region is inherited by loads of things, causes conflicts
        //references.put("Region", "gene");
        references.put("RegulatoryRegion", "gene");
        references.put("SequenceVariant", "genes");
        references.put("FivePrimeUTR", "MRNAs");
        references.put("ThreePrimeUTR", "MRNAs");
        references.put("CDS", "MRNAs");
    }

    /**
     * @see GFF3RecordHandler#process(GFF3Record)
     */
    public void process(GFF3Record record) {
        Item feature = getFeature();

        if (!feature.hasAttribute("curated")) {
            feature.addAttribute(new Attribute("curated", "true"));
        }

        String clsName = XmlUtil.getFragmentFromURI(feature.getClassName());

        // set Gene.organismDbId
        // name set in core - if no name symbol is CGxx?
        // create some synonyms
        if ("Gene".equals(clsName)) {
            Iterator fbgnIter = parseFlyBaseId(record.getDbxrefs(), "FBgn").iterator();
            while (fbgnIter.hasNext()) {
                String organismDbId = (String) fbgnIter.next();
                if (!feature.hasAttribute("organismDbId")) {
                    feature.setAttribute("organismDbId", organismDbId);
                }
                addItem(createSynonym(feature, "identifier", organismDbId));
            }

            // if no name set for gene then use CGxx (FlyBase symbol rules)
            if (feature.getAttribute("symbol") == null) {
                feature.setAttribute("symbol", feature.getAttribute("identifier").getValue());
                addItem(createSynonym(feature, "symbol",
                                      feature.getAttribute("symbol").getValue()));
            }
        }

        // In FlyBase GFF, pseudogenes are modelled as a gene with a pseudogene feature as a child.
        // We fix this by changing the pseudogene to a transcript and then fixing the gene
        // class names later
        if ("Pseudogene".equals(clsName)) {
            String className = tgtNs + "Transcript";
            // Transcript doesn't have a symbol attribute
            getFeature().removeAttribute("symbol");

            getFeature().setClassName(className);

            pseudogeneIds.addAll(record.getParents());
        }

        // set MRNA.organismDbId
        if ("MRNA".equals(clsName)) {
            // FlyBase GFF3 release 4.0 has non-coding RNAs modelled badly (fixed in 4.1)

            Iterator fbIter = parseFlyBaseId(record.getDbxrefs(), "FBtr").iterator();
            while (fbIter.hasNext()) {
                String organismDbId = (String) fbIter.next();
                if (!feature.hasAttribute("organismDbId")) {
                    feature.setAttribute("organismDbId", organismDbId);
                }
                addItem(createSynonym(feature, "identifier", organismDbId));
            }
        }

        // set TransposableElement.organismDbId
        if ("TransposableElement".equals(clsName)) {
            Iterator fbIter = parseFlyBaseId(record.getDbxrefs(), "FBti").iterator();
            while (fbIter.hasNext()) {
                String organismDbId = (String) fbIter.next();
                if (!feature.hasAttribute("organismDbId")) {
                    feature.setAttribute("organismDbId", organismDbId);
                }
                addItem(createSynonym(feature, "identifier", organismDbId));
            }
        }

        // for CDS create additional Translation object
        if ("CDS".equals(clsName)) {
            // CDSs have identifiers like CG1234-PA - we want this to be the Translation
            // identifier, for the CDS add an _CDS to the end.
            String identifier = feature.getAttribute("identifier").getValue();
            feature.setAttribute("identifier", identifier + "_CDS");

            // create and reference additional Translation object, add to polypeptides collection
            Item translation = getItemFactory().makeItem(null, tgtNs + "Translation", "");
            translation.setReference("organism", getOrganism().getIdentifier());
            translation.setAttribute("identifier", identifier);
            if (record.getSource() == null) {
                feature.addAttribute(new Attribute("curated", "true"));
            } else {
                feature.addAttribute(new Attribute("curated", "false"));
            }
            if (feature.getAttribute("symbol") != null) {
                translation.setAttribute("symbol", feature.getAttribute("symbol").getValue());
            }
            translation.addCollection(new ReferenceList("evidence",
                    Arrays.asList(new Object[] {getDataSet().getIdentifier()})));

            addItem(translation);
            addItem(createSynonym(translation, "identifier", identifier));

            feature.addCollection(new ReferenceList("polypeptides",
                new ArrayList(Collections.singleton(translation.getIdentifier()))));

            Iterator fbIter = parseFlyBaseId(record.getDbxrefs(), "FBpp").iterator();
            while (fbIter.hasNext()) {
                String organismDbId = (String) fbIter.next();
                if (!translation.hasAttribute("organismDbId")) {
                    translation.setAttribute("organismDbId", organismDbId);
                }
                addItem(createSynonym(translation, "identifier", organismDbId));
            }
            // TODO add GenBank protein identifier as synonym

        }

        // make sure we have a set with all existing Synonyms and those that will
        // be created by GFF3Converter
        Set synonyms = new HashSet();
        if (feature.hasAttribute("identifier")) {
            synonyms.add(feature.getAttribute("identifier").getValue());
        }
        if (feature.hasAttribute("symbol")) {
            synonyms.add(feature.getAttribute("symbol").getValue());
        }
        if (feature.hasAttribute("organismDbId")) {
            synonyms.add(feature.getAttribute("organismDbId").getValue());
        }
        Iterator itemIter = getItems().iterator();
        while (itemIter.hasNext()) {
            Item item = (Item) itemIter.next();
            if (item.getClassName().endsWith("Synonym")) {
                synonyms.add(item.getAttribute("value").getValue());
            }
        }
        List combined = new ArrayList();

        List list = (List) record.getAttributes().get("synonym_2nd");
        if (list != null) {
            combined.addAll(list);
        }
        list = (List) record.getAttributes().get("synonym");
        if (list != null) {
            combined.addAll(list);
        }

//         if (clsName.equals("SyntenicRegion")) {
//             makeTargetSyntenicRegion(feature, record);
//         }

        Iterator iter = combined.iterator();
        while (iter.hasNext()) {
            String synonym = (String) iter.next();
            if (!synonyms.contains(synonym)) {
                if (synonym.startsWith("CG") || synonym.startsWith("CR")
                    || synonym.startsWith("FB")) {
                    addItem(createSynonym(feature, "identifier", synonym));
                } else {
                    addItem(createSynonym(feature, "symbol", synonym));
                }
                synonyms.add(synonym);
            }
        }

        if ("Gene".equals(clsName)) {
            finalItems.add(getFeature());

            // unset the feature in the Item set so that it doesn't get stored automatically
            removeFeature();
        } else {
            // set references from parent relations
            setReferences(references);
        }
    }

    /**
     * Return items that need extra processing that can only be done after all other GFF features
     * have been read.  For FlyBaseGFF3RecordHandler, the Gene, Pseudogene and targetOrganisms from
     * SyntenicRegion objects are returned.
     * @return the final Items
     */
    public Collection getFinalItems() {
        Map symbolToGeneMap = new HashMap();
        Map organismDbIdToGeneMap = new HashMap();

        Set genesWithDuplicatedSymbols = new LinkedHashSet();
        Set genesWithDuplicatedOrganismDbIds = new LinkedHashSet();

        Iterator finalItemIter = finalItems.iterator();

        while (finalItemIter.hasNext()) {
            Item thisGene = (Item) finalItemIter.next();

            {
                Attribute symbolAtt = thisGene.getAttribute("symbol");

                if (symbolAtt != null) {
                    String symbol = symbolAtt.getValue();

                    Item otherGene = (Item) symbolToGeneMap.get(symbol);

                    if (otherGene == null) {
                        symbolToGeneMap.put(symbol, thisGene);
                    } else {
                        genesWithDuplicatedSymbols.add(otherGene);
                        genesWithDuplicatedSymbols.add(thisGene);
                    }
                }
            }

            {
                Attribute organismDbIdAtt = thisGene.getAttribute("organismDbId");

                if (organismDbIdAtt != null) {
                    String organismDbId = organismDbIdAtt.getValue();

                    Item otherGene = (Item) organismDbIdToGeneMap.get(organismDbId);

                    if (otherGene == null) {
                        organismDbIdToGeneMap.put(organismDbId, thisGene);
                    } else {
                        genesWithDuplicatedOrganismDbIds.add(otherGene);
                        genesWithDuplicatedOrganismDbIds.add(thisGene);
                    }
                }
            }
        }

        Iterator genesWithDuplicatedSymbolsIter = genesWithDuplicatedSymbols.iterator();

        int count = 1;
        while (genesWithDuplicatedSymbolsIter.hasNext()) {
            Item thisGene = (Item) genesWithDuplicatedSymbolsIter.next();

            String newValue =
                thisGene.getAttribute("symbol").getValue() + "-duplicate-symbol-" + count++;

            thisGene.setAttribute("symbol", newValue);

            LOG.warn("gene (" + thisGene.getAttribute("identifier").getValue()
                     + ") has duplicated symbol, created new symbol: " + newValue);
        }

        Iterator genesWithDuplicatedOrganismDbIdsIter = genesWithDuplicatedOrganismDbIds.iterator();

        count = 1;
        while (genesWithDuplicatedOrganismDbIdsIter.hasNext()) {
            Item thisGene = (Item) genesWithDuplicatedOrganismDbIdsIter.next();

            String newValue =
                thisGene.getAttribute("organismDbId").getValue() + "-duplicate-organismDbId-"
                + count++;

            thisGene.setAttribute("organismDbId", newValue);

            LOG.warn("gene (" + thisGene.getAttribute("identifier").getValue()
                     + ") has duplicated organismDbId, created new organismDbId: " + newValue);
        }

        finalItemIter = finalItems.iterator();

        while (finalItemIter.hasNext()) {
            Item thisItem = (Item) finalItemIter.next();

            if (pseudogeneIds.contains(thisItem.getAttribute("identifier").getValue())) {
                thisItem.setClassName(tgtNs + "Pseudogene");
            }
        }

        List retList = new ArrayList();
        retList.addAll(finalItems);

        retList.addAll(otherOrganismItems.values());

        return retList;
    }


    /**
     * @param feature The current Item
     * @param record The current GFF3Record
     */
    protected void makeTargetSyntenicRegion(Item feature, GFF3Record record) {
        String tgtOrgAbbrev = (String) ((List) record.getAttributes().get("to_species")).get(0);
        if (tgtOrgAbbrev.equals("dmel")) {
            tgtOrgAbbrev = "DM";
        } else {
            if (tgtOrgAbbrev.equals("dpse")) {
                tgtOrgAbbrev = "DP";
            } else {
                throw new RuntimeException("unknown organism abbreviation: " + tgtOrgAbbrev);
            }
        }

        Item tgtOrganism = getOrganismItem(tgtOrgAbbrev);

        feature.setReference("targetOrganism", tgtOrganism.getIdentifier());

        Item tgtSyntenicRegion = getItemFactory().makeItem(null, tgtNs + "SyntenicRegion", "");
        tgtSyntenicRegion.setReference("organism", tgtOrganism.getIdentifier());

        // use the same ID on both the current SyntenicRegion and the target SyntenicRegion, but
        // have a different organism
        tgtSyntenicRegion.setAttribute("identifier", record.getId());
        feature.setReference("targetSyntenicRegion", tgtSyntenicRegion);
        tgtSyntenicRegion.setReference("targetSyntenicRegion", feature);

        addItem(tgtSyntenicRegion);
    }

    private Item getOrganismItem(String orgAbbrev) {
        if (otherOrganismItems.containsKey(orgAbbrev)) {
            return (Item) otherOrganismItems.get(orgAbbrev);
        } else {
            Item otherOrganismItem = getItemFactory().makeItem(null, tgtNs + "Organism", "");
            otherOrganismItem.setAttribute("shortName", orgAbbrev);
            otherOrganismItems.put(orgAbbrev, otherOrganismItem);
            return otherOrganismItem;
        }
    }

    /**
     * Clear the list of final items.
     */
    public void clearFinalItems() {
        finalItems.clear();
    }

    /**
     * Create a synonym Item from the given information.
     */
    private Item createSynonym(Item subject, String type, String value) {
        Item synonym = getItemFactory().makeItem(null, tgtNs + "Synonym", "");
        synonym.setAttribute("type", type);
        synonym.setAttribute("value", value);
        synonym.setReference("subject", subject.getIdentifier());
        synonym.setReference("source", getDataSource().getIdentifier());
        return synonym;
    }

    /**
     * Given a list of dbxrefs parse for a single FlyBase identifier with the given
     * prefix.  It is an error if more than identifier is found.
     * @param dbxrefs a list of dbxref strings retrieved from GFF3
     * @param prefix the prefix of a FlyBase identifier type - e.g. FBgn
     * @return the identifier or null if none found
     */
    protected List parseFlyBaseId(List dbxrefs, String prefix) {
        List fbs = new ArrayList();
        if (dbxrefs != null) {
            Iterator iter = dbxrefs.iterator();
            while (iter.hasNext()) {
                String s = (String) iter.next();
                if (s.startsWith("FlyBase:" + prefix)) {
                    fbs.add(s.substring(s.indexOf(":") + 1));
                }
            }
        }
        return fbs;
    }

}
