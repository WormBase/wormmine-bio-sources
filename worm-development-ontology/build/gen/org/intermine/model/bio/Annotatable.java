package org.intermine.model.bio;

public interface Annotatable extends org.intermine.model.InterMineObject
{
    public java.lang.String getPrimaryIdentifier();
    public void setPrimaryIdentifier(final java.lang.String primaryIdentifier);

    public java.util.Set<org.intermine.model.bio.OntologyAnnotation> getOntologyAnnotations();
    public void setOntologyAnnotations(final java.util.Set<org.intermine.model.bio.OntologyAnnotation> ontologyAnnotations);
    public void addOntologyAnnotations(final org.intermine.model.bio.OntologyAnnotation arg);

    public java.util.Set<org.intermine.model.bio.Publication> getPublications();
    public void setPublications(final java.util.Set<org.intermine.model.bio.Publication> publications);
    public void addPublications(final org.intermine.model.bio.Publication arg);

}
