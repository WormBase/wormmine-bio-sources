package org.intermine.model.bio;

public interface Intron extends org.intermine.model.bio.SequenceFeature
{
    public java.util.Set<org.intermine.model.bio.Gene> getGenes();
    public void setGenes(final java.util.Set<org.intermine.model.bio.Gene> genes);
    public void addGenes(final org.intermine.model.bio.Gene arg);

}
