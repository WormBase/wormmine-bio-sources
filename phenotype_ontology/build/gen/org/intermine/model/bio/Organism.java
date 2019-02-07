package org.intermine.model.bio;

public interface Organism extends org.intermine.model.InterMineObject
{
    public java.lang.String getSpecies();
    public void setSpecies(final java.lang.String species);

    public java.lang.String getGenus();
    public void setGenus(final java.lang.String genus);

    public java.lang.String getTaxonId();
    public void setTaxonId(final java.lang.String taxonId);

    public java.lang.String getName();
    public void setName(final java.lang.String name);

    public java.lang.String getCommonName();
    public void setCommonName(final java.lang.String commonName);

    public java.lang.String getShortName();
    public void setShortName(final java.lang.String shortName);

}
