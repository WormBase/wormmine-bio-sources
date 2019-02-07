package org.intermine.model.bio;

public interface OntologyAnnotation extends org.intermine.model.InterMineObject
{
    public java.lang.String getQualifier();
    public void setQualifier(final java.lang.String qualifier);

    public org.intermine.model.bio.Annotatable getSubject();
    public void setSubject(final org.intermine.model.bio.Annotatable subject);
    public void proxySubject(final org.intermine.objectstore.proxy.ProxyReference subject);
    public org.intermine.model.InterMineObject proxGetSubject();

    public org.intermine.model.bio.OntologyTerm getOntologyTerm();
    public void setOntologyTerm(final org.intermine.model.bio.OntologyTerm ontologyTerm);
    public void proxyOntologyTerm(final org.intermine.objectstore.proxy.ProxyReference ontologyTerm);
    public org.intermine.model.InterMineObject proxGetOntologyTerm();

    public java.util.Set<org.intermine.model.bio.DataSet> getDataSets();
    public void setDataSets(final java.util.Set<org.intermine.model.bio.DataSet> dataSets);
    public void addDataSets(final org.intermine.model.bio.DataSet arg);

    public java.util.Set<org.intermine.model.bio.OntologyEvidence> getEvidence();
    public void setEvidence(final java.util.Set<org.intermine.model.bio.OntologyEvidence> evidence);
    public void addEvidence(final org.intermine.model.bio.OntologyEvidence arg);

}
