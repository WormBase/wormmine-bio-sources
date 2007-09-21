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

import java.util.HashMap;
import java.util.Map;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.sql.Database;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.ItemHelper;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DataConverter to read from AnoEST's MySQL database into items
 * @author Kim Rutherford
 */
public class AnoESTConverter extends BioDBConverter
{
    private static final int ANOPHELES_TAXON_ID = 180454;
    private static final String DATASET_TITLE = "AnoEST clusters";
    private static final String DATA_SOURCE_NAME = "VectorBase";
    private Map<String, Item> clusters = new HashMap<String, Item>();
    private Map<String, Item> ests = new HashMap<String, Item>();
    
    /**
     * Create a new AnoESTConverter object.
     * @param database the database to read from
     * @param tgtModel the Model used by the object store we will write to with the ItemWriter
     * @param writer an ItemWriter used to handle the resultant Items
     * @throws ObjectStoreException thrown if ItemWriter.store() fails
     */
    public AnoESTConverter(Database database, Model tgtModel, ItemWriter writer)
        throws ObjectStoreException {
        super(database, tgtModel, writer);
    }

    /**
     * Process the data from the Database and write to the ItemWriter.
     * {@inheritDoc}
     */
    @Override
    public void process() throws Exception {
        Connection connection;
        if (getDatabase() == null) {
            // no Database when testing and no connectio needed
            connection = null;
        } else {
            connection = getDatabase().getConnection();
        }
        makeClusterItems(connection);
        makeEstItems(connection);
    }

    private void makeClusterItems(Connection connection) throws SQLException, ObjectStoreException {
        ResultSet res = getClusterResultSet(connection);

        while (res.next()) {
            String identifier = res.getString(1);
            String chromosomeIdentifier = res.getString(2);
            int start = res.getInt(3);
            int end = res.getInt(4);
            int strand = res.getInt(5);
            
            Item cluster = createItem("ESTCluster");
            cluster.setAttribute("identifier", identifier);
            Item dataSet = getDataSetItem(DATASET_TITLE);
            Item accSynonym = createSynonym(cluster, "identifier", identifier, true,
                                            dataSet, getDataSourceItem(DATA_SOURCE_NAME));
            getItemWriter().store(ItemHelper.convert(accSynonym));
            cluster.setAttribute("curated", "false");
            cluster.setReference("organism", getOrganismItem(ANOPHELES_TAXON_ID));
            cluster.addToCollection("evidence", dataSet);

            // some clusters have no location
            if (chromosomeIdentifier != null && start > 0 && end > 0) {
                makeLocation(chromosomeIdentifier, cluster, start, end, strand, ANOPHELES_TAXON_ID,
                             dataSet);
            }
            getItemWriter().store(ItemHelper.convert(cluster));
            
            clusters.put(identifier, cluster);
        }
    }

    /**
     * This is a protected method so that it can be overriden for testing
     */
    protected ResultSet getClusterResultSet(Connection connection) throws SQLException {
        Statement stmt = connection.createStatement();
        String query = "select id as i1, chr, "
               + "(select min(st) from stable_cluster_ids where id = i1 group by id), "
               + "(select max(nd) from stable_cluster_ids where id = i1 group by id), strand "
               + "from stable_cluster_ids group by id;";
        ResultSet res = stmt.executeQuery(query);
        return res;
    }

    private void makeEstItems(Connection connection) throws SQLException, ObjectStoreException {
        ResultSet res = getEstResultSet(connection);
        while (res.next()) {
            String accession = res.getString(1);
            String clusterId = res.getString(2);
            String cloneId = res.getString(3);
            
            Item est = ests.get(accession);
            if (est == null) {
                est = createItem("EST");
                ests.put(accession, est);
                est.setAttribute("identifier", accession);
                Item dataSet = getDataSetItem(DATASET_TITLE);
                Item accSynonym = createSynonym(est, "identifier", accession, true, dataSet,
                                                getDataSourceItem(DATA_SOURCE_NAME));
                getItemWriter().store(ItemHelper.convert(accSynonym));
                est.setAttribute("curated", "false");
                est.setReference("organism", getOrganismItem(ANOPHELES_TAXON_ID));
                est.addToCollection("evidence", dataSet);
                Item cloneSynonym = createSynonym(est, "identifier", cloneId, false, dataSet,
                                                  getDataSourceItem(DATA_SOURCE_NAME));
                getItemWriter().store(ItemHelper.convert(cloneSynonym));
                Item cluster = clusters.get(clusterId);
                if (cluster != null) {
                    est.addToCollection("ESTClusters", cluster);
                }
                getItemWriter().store(ItemHelper.convert(est));
            }
        }
    }

    /**
     * This is a protected method so that it can be overriden for testing
     */
    protected ResultSet getEstResultSet(Connection connection) throws SQLException {
        Statement stmt = connection.createStatement();
        String query = "select acc, cl_id, clone from est_view;";
        ResultSet res = stmt.executeQuery(query);
        return res;
    }

}
