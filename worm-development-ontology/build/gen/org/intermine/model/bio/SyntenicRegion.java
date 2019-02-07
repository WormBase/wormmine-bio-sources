package org.intermine.model.bio;

public interface SyntenicRegion extends org.intermine.model.bio.SequenceFeature
{
    public org.intermine.model.bio.SyntenyBlock getSyntenyBlock();
    public void setSyntenyBlock(final org.intermine.model.bio.SyntenyBlock syntenyBlock);
    public void proxySyntenyBlock(final org.intermine.objectstore.proxy.ProxyReference syntenyBlock);
    public org.intermine.model.InterMineObject proxGetSyntenyBlock();

}
