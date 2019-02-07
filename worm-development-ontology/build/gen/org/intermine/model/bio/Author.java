package org.intermine.model.bio;

public interface Author extends org.intermine.model.InterMineObject
{
    public java.lang.String getFirstName();
    public void setFirstName(final java.lang.String firstName);

    public java.lang.String getInitials();
    public void setInitials(final java.lang.String initials);

    public java.lang.String getLastName();
    public void setLastName(final java.lang.String lastName);

    public java.lang.String getName();
    public void setName(final java.lang.String name);

    public java.util.Set<org.intermine.model.bio.Publication> getPublications();
    public void setPublications(final java.util.Set<org.intermine.model.bio.Publication> publications);
    public void addPublications(final org.intermine.model.bio.Publication arg);

}
