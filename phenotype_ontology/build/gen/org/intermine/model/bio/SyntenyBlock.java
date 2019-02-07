package org.intermine.model.bio;

public interface SyntenyBlock extends org.intermine.model.InterMineObject
{
    public java.util.Set<org.intermine.model.bio.SyntenicRegion> getSyntenicRegions();
    public void setSyntenicRegions(final java.util.Set<org.intermine.model.bio.SyntenicRegion> syntenicRegions);
    public void addSyntenicRegions(final org.intermine.model.bio.SyntenicRegion arg);

    public java.util.Set<org.intermine.model.bio.DataSet> getDataSets();
    public void setDataSets(final java.util.Set<org.intermine.model.bio.DataSet> dataSets);
    public void addDataSets(final org.intermine.model.bio.DataSet arg);

    public java.util.Set<org.intermine.model.bio.Publication> getPublications();
    public void setPublications(final java.util.Set<org.intermine.model.bio.Publication> publications);
    public void addPublications(final org.intermine.model.bio.Publication arg);

}
