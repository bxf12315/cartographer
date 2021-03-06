/**
 * Copyright (C) 2013 Red Hat, Inc. (jdcasey@commonjava.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.cartographer.graph.util;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.commonjava.cartographer.graph.RelationshipGraphFactory;
import org.commonjava.cartographer.INTERNAL.graph.agg.DefaultGraphAggregator;
import org.commonjava.cartographer.spi.graph.agg.GraphAggregator;
import org.commonjava.cartographer.INTERNAL.graph.discover.DiscovererImpl;
import org.commonjava.cartographer.spi.graph.discover.ProjectRelationshipDiscoverer;
import org.commonjava.cartographer.graph.discover.meta.MetadataScannerSupport;
import org.commonjava.cartographer.graph.discover.meta.ScmUrlScanner;
import org.commonjava.cartographer.graph.discover.patch.PatcherSupport;
import org.commonjava.cartographer.testutil.TestCartoCoreProvider;
import org.commonjava.maven.galley.TransferManager;
import org.commonjava.maven.galley.cache.FileCacheProvider;
import org.commonjava.maven.galley.config.TransportManagerConfig;
import org.commonjava.maven.galley.internal.TransferManagerImpl;
import org.commonjava.maven.galley.internal.xfer.DownloadHandler;
import org.commonjava.maven.galley.internal.xfer.ExistenceHandler;
import org.commonjava.maven.galley.internal.xfer.ListingHandler;
import org.commonjava.maven.galley.internal.xfer.UploadHandler;
import org.commonjava.maven.galley.io.HashedLocationPathGenerator;
import org.commonjava.maven.galley.io.SpecialPathManagerImpl;
import org.commonjava.maven.galley.maven.ArtifactManager;
import org.commonjava.maven.galley.maven.ArtifactMetadataManager;
import org.commonjava.maven.galley.maven.internal.ArtifactManagerImpl;
import org.commonjava.maven.galley.maven.internal.ArtifactMetadataManagerImpl;
import org.commonjava.maven.galley.maven.internal.defaults.StandardMaven304PluginDefaults;
import org.commonjava.maven.galley.maven.internal.defaults.StandardMavenPluginImplications;
import org.commonjava.maven.galley.maven.internal.type.StandardTypeMapper;
import org.commonjava.maven.galley.maven.internal.version.VersionResolverImpl;
import org.commonjava.maven.galley.maven.model.view.XPathManager;
import org.commonjava.maven.galley.maven.parse.MavenMetadataReader;
import org.commonjava.maven.galley.maven.parse.MavenPomReader;
import org.commonjava.maven.galley.maven.parse.XMLInfrastructure;
import org.commonjava.maven.galley.maven.rel.MavenModelProcessor;
import org.commonjava.maven.galley.maven.spi.version.VersionResolver;
import org.commonjava.maven.galley.nfc.MemoryNotFoundCache;
import org.commonjava.maven.galley.spi.cache.CacheProvider;
import org.commonjava.maven.galley.spi.transport.LocationExpander;
import org.commonjava.maven.galley.spi.transport.TransportManager;
import org.commonjava.maven.galley.transport.NoOpLocationExpander;
import org.commonjava.maven.galley.transport.TransportManagerImpl;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class CartoGraphUtilsTest
    extends AbstractCartoGraphUtilsTest
{

    private DefaultGraphAggregator aggregator;

    private ProjectRelationshipDiscoverer discoverer;

    private TestCartoCoreProvider provider;

    private XMLInfrastructure xml;

    private XPathManager xpath;

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private MemoryNotFoundCache nfc;

    @After
    public void teardown()
        throws Exception
    {
        provider.shutdown();
    }

    @Override
    protected void setupComponents()
        throws Exception
    {
        provider = new TestCartoCoreProvider( temp );

        final MavenModelProcessor processor = new MavenModelProcessor();

        // TODO: Do we need to flesh this out??
        final TransportManager transportManager = new TransportManagerImpl();

        nfc = new MemoryNotFoundCache();

        final CacheProvider cacheProvider =
            new FileCacheProvider( temp.newFolder(), new HashedLocationPathGenerator(),
                                   provider.getFileEventManager(), provider.getTransferDecorator() );

        final ExecutorService executor = Executors.newFixedThreadPool( 2 );
        final ExecutorService batchExecutor = Executors.newFixedThreadPool( 2 );

        TransportManagerConfig transportManagerConfig = new TransportManagerConfig();
        final DownloadHandler dh = new DownloadHandler( nfc, transportManagerConfig, executor );
        final UploadHandler uh = new UploadHandler( nfc, transportManagerConfig, executor );
        final ListingHandler lh = new ListingHandler( nfc );
        final ExistenceHandler eh = new ExistenceHandler( nfc );

        final TransferManager transferManager =
            new TransferManagerImpl( transportManager, cacheProvider, nfc, provider.getFileEventManager(), dh, uh, lh,
                                     eh, new SpecialPathManagerImpl(), batchExecutor );

        xml = new XMLInfrastructure();
        xpath = new XPathManager();

        final LocationExpander locationExpander = new NoOpLocationExpander();

        final ArtifactMetadataManager meta = new ArtifactMetadataManagerImpl( transferManager, locationExpander );
        final MavenMetadataReader mmr = new MavenMetadataReader( xml, locationExpander, meta, xpath );

        final VersionResolver versions = new VersionResolverImpl( mmr );

        final ArtifactManager artifacts =
            new ArtifactManagerImpl( transferManager, locationExpander, new StandardTypeMapper(), versions );

        final MavenPomReader pomReader =
            new MavenPomReader( xml, locationExpander, artifacts, xpath, new StandardMaven304PluginDefaults(),
                                new StandardMavenPluginImplications( xml ) );

        // TODO: Add some scanners.
        final MetadataScannerSupport scannerSupport = new MetadataScannerSupport( new ScmUrlScanner( pomReader ) );

        discoverer = new DiscovererImpl( processor, pomReader, artifacts, new PatcherSupport(), scannerSupport );

        aggregator = new DefaultGraphAggregator( discoverer, Executors.newFixedThreadPool( 2 ) );
    }

    @Override
    protected RelationshipGraphFactory getGraphFactory()
        throws IOException
    {
        return provider.getGraphFactory();
    }

    @Override
    protected GraphAggregator getAggregator()
        throws Exception
    {
        return aggregator;
    }

    public XMLInfrastructure getXML()
    {
        return xml;
    }

    public XPathManager getXPath()
    {
        return xpath;
    }

}
