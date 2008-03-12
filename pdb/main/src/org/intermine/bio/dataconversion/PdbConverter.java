package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2008 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.biojava.bio.structure.Structure;
import org.biojava.bio.structure.io.PDBFileParser;
import org.intermine.dataconversion.FileConverter;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;

/**
 * @author Xavier Watkins
 *
 */
public class PdbConverter extends FileConverter
{

    private static final Logger LOG = Logger.getLogger(PdbConverter.class);
    protected static final String ENDL = System.getProperty("line.separator");
    private Item dataSource, dataSet;

    /**
     * Create a new PdbConverter object.
     * @param writer the ItemWriter to store the objects in
     * @param model the Model
     * @throws ObjectStoreException if there is a problem while storing
     */
    public PdbConverter(ItemWriter writer, Model model) throws ObjectStoreException {
        super(writer, model);
        dataSource = createItem("DataSource");
        dataSource.setAttribute("name", "The RCSB Protein Data Bank (PDB)");
        dataSet = createItem("DataSet");
        dataSet.setAttribute("title", "PDB data - dmel");
        dataSet.setAttribute("url", "http://www.rcsb.org/pdb/");
        store(dataSet);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(Reader reader) throws Exception {
        File currentFile = getCurrentFile();
        if (currentFile.getName().endsWith(".pdb")) {
            Item proteinStructure = createItem("ProteinStructure");

            PDBFileParser pdbfileparser = new PDBFileParser();
            PdbBufferedReader pdbBuffReader = new PdbBufferedReader(reader);
            Structure structure = pdbfileparser.parsePDBFile(pdbBuffReader);
            String atm = structure.toPDB();

            String idCode = (String) structure.getHeader().get("idCode");
            proteinStructure.setAttribute("identifier", idCode);

            List<String> proteins = new ArrayList<String>();
            List<String> dbrefs = pdbBuffReader.getDbrefs();
            for (String accnum: dbrefs) {
                Item protein = getAndStoreItemOnce("Protein", "primaryAccession", accnum);
                proteins.add(protein.getIdentifier());
            }

            String title = (String) structure.getHeader().get("title");
            if (title != null && !title.equals("")) {
                proteinStructure.setAttribute("title", (String) structure.getHeader().get("title"));
            } else {
                LOG.warn("No value for title in structure: " + idCode);
            }
            String technique = (String) structure.getHeader().get("technique");
            if (technique != null && !technique.equals("")) {
                proteinStructure.setAttribute("technique", technique);
            } else {
                LOG.warn("No value for technique in structure: " + idCode);
            }
            proteinStructure.setAttribute("classification",
                                          (String) structure.getHeader().get("classification"));
            Object resolution = structure.getHeader().get("resolution");
            if (resolution instanceof Float) {
                final Float resolutionFloat = (Float) structure.getHeader().get("resolution");
                proteinStructure.setAttribute("resolution", Float.toString(resolutionFloat));
            }

            proteinStructure.setAttribute("atm", atm);
            proteinStructure.setCollection("proteins", proteins);
            proteinStructure.addToCollection("evidence", dataSet);

            store(proteinStructure);
        }
    }


    /**
     * BioJava doesn't support getting DBREF so we get it as the file is read.
     *
     * @author Xavier Watkins
     *
     */
    public class PdbBufferedReader extends BufferedReader
    {

        private List<String> dbrefs = new ArrayList<String>();

        /**
         * Create a new PdbBufferedReader object.
         * @param reader the underlying Reader object
         */
        public PdbBufferedReader(Reader reader) {
            super(reader);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String readLine() throws IOException {
            String line = super.readLine();
            if (line != null && line.matches("^DBREF.*")) {
                String [] split = line.split("\\s+");
                if (split[5].equals("SWS") || split[5].equals("UNP")) {
                    dbrefs.add(split[6]);
                }
            }
            return line;
        }

        /**
         * Return the db refs read from the Reader.
         * @return the List of db refs
         */
        public List getDbrefs() {
            return dbrefs;
        }
    }


}
