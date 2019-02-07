package org.intermine.model.bio;

public interface Publication extends org.intermine.model.InterMineObject
{
    public java.lang.Integer getYear();
    public void setYear(final java.lang.Integer year);

    public java.lang.String getIssue();
    public void setIssue(final java.lang.String issue);

    public java.lang.String getTitle();
    public void setTitle(final java.lang.String title);

    public java.lang.String getPages();
    public void setPages(final java.lang.String pages);

    public java.lang.String getDoi();
    public void setDoi(final java.lang.String doi);

    public java.lang.String getVolume();
    public void setVolume(final java.lang.String volume);

    public java.lang.String getJournal();
    public void setJournal(final java.lang.String journal);

    public java.lang.String getFirstAuthor();
    public void setFirstAuthor(final java.lang.String firstAuthor);

    public java.lang.String getMonth();
    public void setMonth(final java.lang.String month);

    public java.lang.String getAbstractText();
    public void setAbstractText(final java.lang.String abstractText);

    public java.lang.String getPubMedId();
    public void setPubMedId(final java.lang.String pubMedId);

    public java.util.Set<org.intermine.model.bio.Author> getAuthors();
    public void setAuthors(final java.util.Set<org.intermine.model.bio.Author> authors);
    public void addAuthors(final org.intermine.model.bio.Author arg);

    public java.util.Set<org.intermine.model.bio.Annotatable> getEntities();
    public void setEntities(final java.util.Set<org.intermine.model.bio.Annotatable> entities);
    public void addEntities(final org.intermine.model.bio.Annotatable arg);

    public java.util.Set<org.intermine.model.bio.MeshTerm> getMeshTerms();
    public void setMeshTerms(final java.util.Set<org.intermine.model.bio.MeshTerm> meshTerms);
    public void addMeshTerms(final org.intermine.model.bio.MeshTerm arg);

}
