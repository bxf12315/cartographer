/**
 * Copyright (C) 2012 Red Hat, Inc. (jdcasey@commonjava.org)
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
package org.commonjava.cartographer.graph.spi.neo4j.io;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.commonjava.cartographer.graph.ViewParams;
import org.commonjava.maven.atlas.graph.jackson.ProjectRelationshipSerializerModule;
import org.commonjava.maven.atlas.graph.rel.DependencyRelationship;
import org.commonjava.maven.atlas.graph.rel.PluginDependencyRelationship;
import org.commonjava.maven.atlas.graph.rel.PluginRelationship;
import org.commonjava.maven.atlas.graph.rel.ProjectRelationship;
import org.commonjava.cartographer.graph.spi.neo4j.GraphAdmin;
import org.commonjava.cartographer.graph.spi.neo4j.GraphRelType;
import org.commonjava.cartographer.graph.spi.neo4j.NodeType;
import org.commonjava.cartographer.graph.spi.neo4j.model.AbstractNeoProjectRelationship;
import org.commonjava.cartographer.graph.spi.neo4j.model.CyclePath;
import org.commonjava.cartographer.graph.spi.neo4j.model.NeoArtifactRef;
import org.commonjava.cartographer.graph.spi.neo4j.model.NeoBomRelationship;
import org.commonjava.cartographer.graph.spi.neo4j.model.NeoDependencyRelationship;
import org.commonjava.cartographer.graph.spi.neo4j.model.NeoExtensionRelationship;
import org.commonjava.cartographer.graph.spi.neo4j.model.NeoParentRelationship;
import org.commonjava.cartographer.graph.spi.neo4j.model.NeoPluginDependencyRelationship;
import org.commonjava.cartographer.graph.spi.neo4j.model.NeoPluginRelationship;
import org.commonjava.cartographer.graph.spi.neo4j.model.NeoProjectVersionRef;
import org.commonjava.cartographer.graph.spi.neo4j.model.NeoTypeAndClassifier;
import org.commonjava.maven.atlas.ident.jackson.ProjectVersionRefSerializerModule;
import org.commonjava.maven.atlas.ident.ref.ArtifactRef;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.lang.StringUtils.join;

public final class Conversions
{

    public static final String RELATIONSHIP_ID = "relationship_id";

    public static final String GROUP_ID = "groupId";

    public static final String ARTIFACT_ID = "artifactId";

    public static final String VERSION = "version";

    public static final String GAV = "gav";

    public static final String GA = "ga";

    public static final String INDEX = "index";

    public static final String IS_REPORTING_PLUGIN = "reporting";

    public static final String IS_MANAGED = "managed";

    public static final String IS_INHERITED = "inherited";

    public static final String IS_MIXIN = "mixin";

    public static final String PLUGIN_GROUP_ID = "plugin_groupId";

    public static final String PLUGIN_ARTIFACT_ID = "plugin_artifactId";

    public static final String TYPE = "type";

    public static final String CLASSIFIER = "classifier";

    public static final String SCOPE = "scope";

    public static final String OPTIONAL = "optional";

    public static final String EXCLUDES = "excludes";

    public static final String CYCLE_ID = "cycle_id";

    public static final String CYCLE_RELATIONSHIPS = "relationship_participants";

    public static final String CYCLE_PROJECTS = "project_participants";

    public static final String PROJECT_ERROR = "_error";

    private static final String METADATA_PREFIX = "_metadata_";

    public static final String NODE_TYPE = "_node_type";

    public static final String CYCLE_MEMBERSHIP = "cycle_membership";

    public static final String VARIABLE = "_variable";

    public static final String CONNECTED = "_connected";

    public static final String CYCLE_INJECTION = "_cycle_injection";

    public static final String CYCLES_INJECTED = "_cycles";

    public static final String SOURCE_URI = "source_uri";

    public static final String POM_LOCATION_URI = "pom_location_uri";

    public static final String LAST_ACCESS_DATE = "last_access";

    // graph-level configuration.

    public static final String LAST_ACCESS = "last_access";

    public static final String ACTIVE_POM_LOCATIONS = "active-pom-locations";

    public static final String ACTIVE_SOURCES = "active-pom-sources";

    public static final String CONFIG_PROPERTY_PREFIX = "_p_";

    public static final String VIEW_SHORT_ID = "view_sid";

    private static final String VIEW_DATA = "view_data";

    // cached path tracking...ONLY handled by Conversions, since the info is inlined.

    private static final String CYCLE_PATH_PREFIX = "cached_cycle_";

    // handled by other things, like updaters.

    public static final String RID = "rel_id";

    public static final String NID = "node_id";

    public static final String CONFIG_ID = "config_id";

    public static final String VIEW_ID = "view_id";

    public static final String CYCLE_DETECTION_PENDING = "cycle_detect_pending";

    private static final String MEMBERSHIP_DETECTION_PENDING = "member_detect_pending";

    private Conversions()
    {
    }

    public static int countArrayElements( final String property, final PropertyContainer container )
    {
        if ( !container.hasProperty( property ) )
        {
            return -1;
        }

        final Object value = container.getProperty( property );
        if ( value.getClass().isArray() )
        {
            final Object[] elements = (Object[]) value;
            return elements.length;
        }

        return 1;
    }

    public static List<ProjectVersionRef> convertToDetachedProjects( final Iterable<Node> nodes )
    {
        final List<ProjectVersionRef> refs = new ArrayList<ProjectVersionRef>();
        for ( final Node node : nodes )
        {
            if ( node.getId() == 0 )
            {
                continue;
            }

            if ( !Conversions.isType( node, NodeType.PROJECT ) )
            {
                continue;
            }

            refs.add( Conversions.toProjectVersionRef( node ).detach() );
        }

        return refs;
    }

    public static List<NeoProjectVersionRef> convertToProjects( final Iterable<Node> nodes )
    {
        final List<NeoProjectVersionRef> refs = new ArrayList<NeoProjectVersionRef>();
        for ( final Node node : nodes )
        {
            if ( node.getId() == 0 )
            {
                continue;
            }

            if ( !Conversions.isType( node, NodeType.PROJECT ) )
            {
                continue;
            }

            refs.add( Conversions.toProjectVersionRef( node ) );
        }

        return refs;
    }

    /**
     * Converts an iterable of relationships to project relationships detached from database.
     *
     * @param relationships iterable of {@link AbstractNeoProjectRelationship} or {@link Relationship}
     * @return list of detached relationships
     * @throws IllegalArgumentException if an iterable of unsupported classes is passed
     */
    public static List<ProjectRelationship<?, ?>> convertToDetachedRelationships( final Iterable<?> relationships )
    {
        final List<ProjectRelationship<?, ?>> rels = new ArrayList<ProjectRelationship<?, ?>>();
        Iterator<?> iterator = relationships.iterator();
        while ( iterator.hasNext() )
        {
            final AbstractNeoProjectRelationship<?, ?, ?> rel;
            Object next = iterator.next();
            if ( next instanceof AbstractNeoProjectRelationship )
            {
                rel = ( AbstractNeoProjectRelationship<?, ?, ?>) next;
            }
            else if ( next instanceof Relationship )
            {
                rel = Conversions.toProjectRelationship( ( Relationship ) next );
            }
            else
            {
                throw new IllegalArgumentException( "Relationship class " + next.getClass().getCanonicalName()
                                                  + " cannot be converted to detached relationship." );
            }
            if ( rel != null )
            {
                rels.add( rel.detach() );
            }
        }

        return rels;
    }

    public static List<AbstractNeoProjectRelationship<?, ?, ?>> convertToRelationships( final Iterable<Relationship> relationships )
    {
        final List<AbstractNeoProjectRelationship<?, ?, ?>> rels = new ArrayList<AbstractNeoProjectRelationship<?, ?, ?>>();
        for ( final Relationship relationship : relationships )
        {
            final AbstractNeoProjectRelationship<?, ?, ?> rel = Conversions.toProjectRelationship( relationship );
            if ( rel != null )
            {
                rels.add( rel );
            }
        }

        return rels;
    }

    public static List<ProjectRelationship<?, ?>> convertToDetachedRelationships( final Iterable<Long> relationships,
                                                                          final GraphAdmin admin )
    {
        final List<ProjectRelationship<?, ?>> rels = new ArrayList<ProjectRelationship<?, ?>>();
        for ( final Long rid : relationships )
        {
            final Relationship relationship = admin.getRelationship( rid );
            final ProjectRelationship<?, ?> rel = toProjectRelationship( relationship ).detach();
            if ( rel != null )
            {
                rels.add( rel );
            }
        }

        return rels;
    }

    public static List<AbstractNeoProjectRelationship<?, ?, ?>> convertToRelationships( final Iterable<Long> relationships,
                                                                          final GraphAdmin admin )
    {
        final List<AbstractNeoProjectRelationship<?, ?, ?>> rels = new ArrayList<AbstractNeoProjectRelationship<?, ?, ?>>();
        for ( final Long rid : relationships )
        {
            final Relationship relationship = admin.getRelationship( rid );
            final AbstractNeoProjectRelationship<?, ?, ?> rel = toProjectRelationship( relationship );
            if ( rel != null )
            {
                rels.add( rel );
            }
        }

        return rels;
    }

    public static void toNodeProperties( final ProjectVersionRef ref, final Node node, final boolean connected )
    {
        Logger logger = LoggerFactory.getLogger( Conversions.class );

        logger.debug( "Adding {} (type: {}) to node: {}", ref, ref.getClass().getSimpleName(), node );
        if ( !( ref instanceof NeoProjectVersionRef ) || ( (NeoProjectVersionRef) ref ).isDirty() )
        {
            final String g = ref.getGroupId();
            final String a = ref.getArtifactId();
            final String v = ref.getVersionString();

            if ( empty( g ) || empty( a ) || empty( v ) )
            {
                throw new IllegalArgumentException( String.format( "GAV cannot contain nulls: %s:%s:%s", g, a, v ) );
            }

            node.setProperty( ARTIFACT_ID, a );
            node.setProperty( GROUP_ID, g );

            logger.debug( "Setting property: {} with value: {} for node: {}", VERSION, v, node.getId() );
            node.setProperty( VERSION, v );

            node.setProperty( GAV, ref.toString() );
        }

        node.setProperty( NODE_TYPE, NodeType.PROJECT.name() );

        if ( ref.isVariableVersion() )
        {
            node.setProperty( VARIABLE, true );
        }

        markConnected( node, connected );
        //
        //        logger.debug( "groupId of {} is:\nNeoIdentityUtils: {}\nConversions: {}\nDirect access: {}", node,
        //                      NeoIdentityUtils.getStringProperty( node, GROUP_ID, null, null ),
        //                      getStringProperty( GROUP_ID, node ), node.hasProperty( GROUP_ID ) ? node.getProperty( GROUP_ID ) : null );
    }

    public static boolean isAtlasType( final Relationship rel )
    {
        return GraphRelType.valueOf( rel.getType().name() ).isAtlasRelationship();
    }

    public static boolean isType( final Node node, final NodeType type )
    {
        final String nt = getStringProperty( NODE_TYPE, node );
        return nt != null && type == NodeType.valueOf( nt );
    }

    //    public static ProjectVersionRef toProjectVersionRef( final Node node )
    //    {
    //        return toProjectVersionRef( node, null );
    //    }

    public static NeoProjectVersionRef toProjectVersionRef( final Node node )
    {
        if ( node == null )
        {
            return null;
        }

        if ( !isType( node, NodeType.PROJECT ) )
        {
            throw new IllegalArgumentException( "Node " + node.getId() + " is not a project reference." );
        }

        final NeoProjectVersionRef result = new NeoProjectVersionRef( node );
        return result;
    }

    private static boolean empty( final String val )
    {
        return val == null || val.trim().length() < 1;
    }

    @SuppressWarnings( "incomplete-switch" )
    public static void toRelationshipProperties( final ProjectRelationship<?, ?> rel, final Relationship relationship )
    {
        relationship.setProperty( INDEX, rel.getIndex() );
        String[] srcs = toStringArray( rel.getSources() );
        Logger logger = LoggerFactory.getLogger( Conversions.class );
        logger.debug( "Storing rel: {}\nwith sources: {}\n in property: {}\nRelationship: {}", rel,
                      Arrays.toString( srcs ), SOURCE_URI, relationship );
        relationship.setProperty( SOURCE_URI, srcs );
        relationship.setProperty( POM_LOCATION_URI, rel.getPomLocation().toString() );
        relationship.setProperty( IS_INHERITED, rel.isInherited() );

        switch ( rel.getType() )
        {
            case BOM:
                relationship.setProperty( IS_MIXIN, rel.isMixin() );
                break;
            case DEPENDENCY:
            {
                final DependencyRelationship specificRel = (DependencyRelationship) rel;
                toRelationshipProperties( (ArtifactRef) rel.getTarget(), relationship );
                relationship.setProperty( IS_MANAGED, specificRel.isManaged() );
                relationship.setProperty( SCOPE, specificRel.getScope().realName() );
                relationship.setProperty( OPTIONAL, specificRel.isOptional() );

                final Set<ProjectRef> excludes = specificRel.getExcludes();
                if ( excludes != null && !excludes.isEmpty() )
                {
                    final StringBuilder sb = new StringBuilder();
                    for ( final ProjectRef exclude : excludes )
                    {
                        if ( sb.length() > 0 )
                        {
                            sb.append( "," );
                        }

                        sb.append( exclude.getGroupId() ).append( ":" ).append( exclude.getArtifactId() );
                    }

                    relationship.setProperty( EXCLUDES, sb.toString() );
                }

                break;
            }
            case PLUGIN_DEP:
            {
                toRelationshipProperties( (ArtifactRef) rel.getTarget(), relationship );

                final PluginDependencyRelationship specificRel = (PluginDependencyRelationship) rel;

                final ProjectRef plugin = specificRel.getPlugin();
                relationship.setProperty( PLUGIN_ARTIFACT_ID, plugin.getArtifactId() );
                relationship.setProperty( PLUGIN_GROUP_ID, plugin.getGroupId() );
                relationship.setProperty( IS_MANAGED, specificRel.isManaged() );

                break;
            }
            case PLUGIN:
            {
                final PluginRelationship specificRel = (PluginRelationship) rel;
                relationship.setProperty( IS_MANAGED, specificRel.isManaged() );
                relationship.setProperty( IS_REPORTING_PLUGIN, specificRel.isReporting() );

                break;
            }
        }
    }

    public static String[] toStringArray( final Collection<?> sources )
    {
        final Set<String> result = new LinkedHashSet<String>( sources.size() );
        for ( final Object object : sources )
        {
            if ( object == null )
            {
                continue;
            }

            result.add( object.toString() );
        }

        return result.toArray( new String[result.size()] );
    }

    //    public static ProjectRelationship<?> toProjectRelationship( final Relationship rel )
    //    {
    //        return toProjectRelationship( rel, null );
    //    }

    public static AbstractNeoProjectRelationship<?, ?, ?> toProjectRelationship( final Relationship rel )
    {
        if ( rel == null )
        {
            return null;
        }

        final GraphRelType mapper = GraphRelType.valueOf( rel.getType().name() );

        //        LOGGER.debug( "Converting relationship of type: {} (atlas type: {})", mapper,
        //                                              mapper.atlasType() );

        if ( !mapper.isAtlasRelationship() )
        {
            return null;
        }

        if ( rel.getStartNode() == null || rel.getEndNode() == null || !isType( rel.getStartNode(), NodeType.PROJECT )
                || !isType( rel.getEndNode(), NodeType.PROJECT ) )
        {
            return null;
        }

        AbstractNeoProjectRelationship<?, ?, ?> result = null;
        switch ( mapper.atlasType() )
        {
            case DEPENDENCY:
            {
                result = new NeoDependencyRelationship( rel );
                break;
            }
            case PLUGIN_DEP:
            {
                result = new NeoPluginDependencyRelationship( rel );
                break;
            }
            case PLUGIN:
            {
                result = new NeoPluginRelationship( rel );
                break;
            }
            case EXTENSION:
            {
                result = new NeoExtensionRelationship( rel );
                break;
            }
            case BOM:
            {
                result = new NeoBomRelationship( rel );
                break;
            }
            case PARENT:
            {
                result = new NeoParentRelationship( rel );
                break;
            }
            default:
            {
                throw new IllegalArgumentException(
                        "I don't know how to construct the atlas relationship for type: " + mapper.atlasType() );
            }
        }

        //        LOGGER.debug( "Returning project relationship: {}", result );
        return result;
    }

    public static String id( final ProjectRelationship<?, ?> rel )
    {
        try
        {
            String json = newMapper().writeValueAsString( rel );

            // FIXME: Rookie mistake! You can't add debug info to toString() with this here.
            return DigestUtils.shaHex( json );
        }
        catch ( JsonProcessingException e )
        {
            throw new IllegalArgumentException( "Cannot serialize relationship for ID generation: " + e.getMessage(),
                                                e );
        }
    }

    private static ObjectMapper newMapper()
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModules( ProjectVersionRefSerializerModule.INSTANCE,
                                ProjectRelationshipSerializerModule.INSTANCE,
                                NeoSpecificProjectRelationshipSerializerModule.INSTANCE,
                                NeoSpecificProjectVersionRefSerializerModule.INSTANCE );

        mapper.disable( SerializationFeature.WRITE_NULL_MAP_VALUES, SerializationFeature.WRITE_EMPTY_JSON_ARRAYS );
        mapper.disable( DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES );

        return mapper;
    }

    private static ArtifactRef toArtifactRef( final ProjectVersionRef ref, final Relationship rel )
    {
        if ( ref == null )
        {
            return null;
        }

        return new NeoArtifactRef( ref, new NeoTypeAndClassifier( rel ) );
    }

    private static void toRelationshipProperties( final ArtifactRef target, final Relationship relationship )
    {
        Logger logger = LoggerFactory.getLogger( Conversions.class );
        logger.debug( "Type of artifact: {} (type: {}) is: {}", target, target.getClass().getSimpleName(),
                      target.getType() );
        relationship.setProperty( TYPE, target.getType() );

        if ( target.getClassifier() != null )
        {
            relationship.setProperty( CLASSIFIER, target.getClassifier() );
        }
    }

    public static String getStringProperty( final String prop, final PropertyContainer container )
    {
        if ( container.hasProperty( prop ) )
        {
            return (String) container.getProperty( prop );
        }
        return null;
    }

    public static Set<URI> getURISetProperty( final String prop, final PropertyContainer container,
                                              final URI defaultValue )
    {
        final Set<URI> result = new HashSet<URI>();

        if ( container.hasProperty( prop ) )
        {
            final String[] uris = (String[]) container.getProperty( prop );
            for ( final String uri : uris )
            {
                try
                {
                    final URI u = new URI( uri );
                    if ( !result.contains( u ) )
                    {
                        result.add( u );
                    }
                }
                catch ( final URISyntaxException e )
                {
                    Logger logger = LoggerFactory.getLogger( Conversions.class );
                    logger.warn(
                            String.format( "Failed to construct URI from: %s\nContainer: %s\nReason: %s", container,
                                           uri, e.getMessage() ), e );
                }
            }
        }

        if ( defaultValue != null && result.isEmpty() )
        {
            result.add( defaultValue );
        }

        return result;
    }

    public static void addToURISetProperty( final Collection<URI> uris, final String prop,
                                            final PropertyContainer container )
    {
        if ( uris == null || uris.isEmpty() )
        {
            return;
        }

        final Set<URI> existing = getURISetProperty( prop, container, null );
        for ( final URI uri : uris )
        {
            existing.add( uri );
        }

        container.setProperty( prop, toStringArray( existing ) );
    }

    public static void removeFromURISetProperty( final Collection<URI> uris, final String prop,
                                                 final PropertyContainer container )
    {
        if ( uris == null || uris.isEmpty() || !container.hasProperty( prop ) )
        {
            return;
        }

        final Set<URI> existing = getURISetProperty( prop, container, null );
        for ( final URI uri : uris )
        {
            existing.remove( uri );
        }

        if ( existing.isEmpty() )
        {
            container.removeProperty( prop );
        }
        else
        {
            container.setProperty( prop, toStringArray( existing ) );
        }
    }

    public static URI getURIProperty( final String prop, final PropertyContainer container, final URI defaultValue )
    {
        URI result = defaultValue;

        if ( container.hasProperty( prop ) )
        {
            try
            {
                result = new URI( (String) container.getProperty( prop ) );
            }
            catch ( final URISyntaxException e )
            {
            }
        }

        return result;
    }

    public static Boolean getBooleanProperty( final String prop, final PropertyContainer container )
    {
        if ( container.hasProperty( prop ) )
        {
            return (Boolean) container.getProperty( prop );
        }
        return null;
    }

    public static Boolean getBooleanProperty( final String prop, final PropertyContainer container,
                                              final Boolean defaultValue )
    {
        if ( container.hasProperty( prop ) )
        {
            return (Boolean) container.getProperty( prop );
        }

        return defaultValue;
    }

    public static Integer getIntegerProperty( final String prop, final PropertyContainer container )
    {
        if ( container.hasProperty( prop ) )
        {
            return (Integer) container.getProperty( prop );
        }
        return null;
    }

    public static Integer getIntegerProperty( final String prop, final PropertyContainer container,
                                              final int defaultValue )
    {
        if ( container.hasProperty( prop ) )
        {
            return (Integer) container.getProperty( prop );
        }

        return defaultValue;
    }

    public static Long getLongProperty( final String prop, final PropertyContainer container, final long defaultValue )
    {
        if ( container.hasProperty( prop ) )
        {
            return (Long) container.getProperty( prop );
        }

        return defaultValue;
    }

    public static String setConfigProperty( final String key, final String value, final PropertyContainer container )
    {
        final String pkey = CONFIG_PROPERTY_PREFIX + key;
        final String old = container.hasProperty( pkey ) ? (String) container.getProperty( pkey ) : null;

        container.setProperty( pkey, value );

        return old;
    }

    public static String removeConfigProperty( final String key, final PropertyContainer container )
    {
        final String pkey = CONFIG_PROPERTY_PREFIX + key;
        String old = null;
        if ( container.hasProperty( pkey ) )
        {
            old = (String) container.getProperty( pkey );

            container.removeProperty( pkey );
        }

        return old;
    }

    public static String getConfigProperty( final String key, final PropertyContainer container,
                                            final String defaultValue )
    {
        final String result = getStringProperty( CONFIG_PROPERTY_PREFIX + key, container );

        return result == null ? defaultValue : result;
    }

    public static void setMetadata( final String key, final String value, final PropertyContainer container )
    {
        container.setProperty( METADATA_PREFIX + key, value );
    }

    public static void setMetadata( final Map<String, String> metadata, final PropertyContainer container )
    {
        final Map<String, String> metadataMap = getMetadataMap( container );

        if ( metadataMap != null )
        {
            for ( final String key : metadataMap.keySet() )
            {
                container.removeProperty( key );
            }
        }

        for ( final Map.Entry<String, String> entry : metadata.entrySet() )
        {
            container.setProperty( METADATA_PREFIX + entry.getKey(), entry.getValue() );
        }
    }

    public static Map<String, String> getMetadataMap( final PropertyContainer container )
    {
        return getMetadataMap( container, null );
    }

    public static Map<String, String> getMetadataMap( final PropertyContainer container, final Set<String> matching )
    {
        final Iterable<String> keys = container.getPropertyKeys();
        final Map<String, String> md = new HashMap<String, String>();
        for ( final String key : keys )
        {
            if ( !key.startsWith( METADATA_PREFIX ) )
            {
                continue;
            }

            final String k = key.substring( METADATA_PREFIX.length() );
            if ( matching != null && !matching.contains( k ) )
            {
                continue;
            }

            final String value = getStringProperty( key, container );

            md.put( k, value );
        }

        return md.isEmpty() ? null : md;
    }

    public static String getMetadata( final String key, final PropertyContainer container )
    {
        return getStringProperty( METADATA_PREFIX + key, container );
    }

    public static void toNodeProperties( final String cycleId, final String rawCycleId,
                                         final Set<ProjectVersionRef> refs, final Node node )
    {
        node.setProperty( NODE_TYPE, NodeType.CYCLE.name() );
        node.setProperty( CYCLE_ID, cycleId );
        node.setProperty( CYCLE_RELATIONSHIPS, rawCycleId );
        node.setProperty( CYCLE_PROJECTS, join( refs, "," ) );
    }

    public static boolean isConnected( final Node node )
    {
        return getBooleanProperty( CONNECTED, node );
    }

    public static void markConnected( final Node node, final boolean connected )
    {
        //        LOGGER.info( "Marking as connected (non-missing): {}", node.getProperty( GAV ) );
        node.setProperty( CONNECTED, connected );
    }

    public static void markCycleInjection( final Relationship relationship, final Set<List<Relationship>> cycles )
    {
        relationship.setProperty( CYCLE_INJECTION, true );
        final List<Long> collapsed = new ArrayList<Long>();
        final Set<List<Long>> existing = getInjectedCycles( relationship );
        if ( existing != null && !existing.isEmpty() )
        {
            for ( final List<Long> cycle : existing )
            {
                if ( !collapsed.isEmpty() )
                {
                    collapsed.add( -1L );
                }

                collapsed.addAll( cycle );
            }
        }

        for ( final List<Relationship> cycle : cycles )
        {
            if ( existing.contains( cycle ) )
            {
                continue;
            }

            if ( !collapsed.isEmpty() )
            {
                collapsed.add( -1L );
            }

            boolean containsGivenRelationship = false;
            for ( final Relationship r : cycle )
            {
                final long rid = r.getId();

                collapsed.add( rid );
                if ( rid == relationship.getId() )
                {
                    containsGivenRelationship = true;
                }
            }

            if ( !containsGivenRelationship )
            {
                collapsed.add( relationship.getId() );
            }
        }

        final long[] arry = new long[collapsed.size()];
        int i = 0;
        for ( final Long l : collapsed )
        {
            arry[i] = l;
            i++;
        }

        relationship.setProperty( CYCLES_INJECTED, arry );
    }

    public static Set<List<Long>> getInjectedCycles( final Relationship relationship )
    {
        final Set<List<Long>> cycles = new HashSet<List<Long>>();

        if ( relationship.hasProperty( CYCLES_INJECTED ) )
        {
            final long[] collapsed = (long[]) relationship.getProperty( CYCLES_INJECTED );

            List<Long> currentCycle = new ArrayList<Long>();
            for ( final long id : collapsed )
            {
                if ( id == -1 )
                {
                    if ( !currentCycle.isEmpty() )
                    {
                        cycles.add( currentCycle );
                        currentCycle = new ArrayList<Long>();
                    }
                }
                else
                {
                    currentCycle.add( id );
                }
            }

            if ( !currentCycle.isEmpty() )
            {
                cycles.add( currentCycle );
            }
        }

        return cycles;
    }

    public static void removeProperty( final String key, final PropertyContainer container )
    {
        if ( container.hasProperty( key ) )
        {
            container.removeProperty( key );
        }
    }

    public static <T, P> Set<P> toProjectedSet( final Iterable<T> src, final Projector<T, P> projector )
    {
        final Set<P> set = new HashSet<P>();
        for ( final T t : src )
        {
            set.add( projector.project( t ) );
        }

        return set;
    }

    public static <T> Set<T> toSet( final Iterable<T> src )
    {
        final Set<T> set = new HashSet<T>();
        for ( final T t : src )
        {
            set.add( t );
        }

        return set;
    }

    public static <T> List<T> toList( final Iterable<T> src )
    {
        final List<T> set = new ArrayList<T>();
        for ( final T t : src )
        {
            set.add( t );
        }

        return set;
    }

    public static void cloneRelationshipProperties( final Relationship from, final Relationship to )
    {
        final Iterable<String> keys = from.getPropertyKeys();
        for ( final String key : keys )
        {
            to.setProperty( key, from.getProperty( key ) );
        }
    }

    public static void storeCachedCyclePath( final CyclePath path, final Node viewNode )
    {
        viewNode.setProperty( CYCLE_PATH_PREFIX + path.getKey(), path.getRelationshipIds() );
    }

    public static Set<CyclePath> getCachedCyclePaths( final Node viewNode )
    {
        final Set<CyclePath> cycles = new HashSet<CyclePath>();
        for ( final String key : viewNode.getPropertyKeys() )
        {
            if ( key.startsWith( CYCLE_PATH_PREFIX ) )
            {
                final long[] ids = (long[]) viewNode.getProperty( key );
                cycles.add( new CyclePath( ids ) );
            }
        }

        return cycles;
    }

    public static void storeView( final ViewParams params, final Node viewNode )
    {
        viewNode.setProperty( Conversions.VIEW_SHORT_ID, params.getShortId() );

        ObjectOutputStream oos = null;
        try
        {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();

            oos = new ObjectOutputStream( baos );
            oos.writeObject( params );

            viewNode.setProperty( VIEW_DATA, baos.toByteArray() );
        }
        catch ( final IOException e )
        {
            throw new IllegalStateException( "Cannot construct ObjectOutputStream to wrap ByteArrayOutputStream!", e );
        }
        finally
        {
            IOUtils.closeQuietly( oos );
        }
    }

    //    public static GraphView retrieveView( final Node viewNode, final AbstractNeo4JEGraphDriver driver )
    //    {
    //        return retrieveView( viewNode, null, driver );
    //    }

    public static ViewParams retrieveView( final Node viewNode, final GraphAdmin maint )
    {
        if ( !viewNode.hasProperty( VIEW_DATA ) )
        {
            return null;
        }

        final byte[] data = (byte[]) viewNode.getProperty( VIEW_DATA );

        ObjectInputStream ois = null;
        try
        {
            ois = new ObjectInputStream( new ByteArrayInputStream( data ) );
            final ViewParams view = (ViewParams) ois.readObject();

            return view;
        }
        catch ( final IOException e )
        {
            throw new IllegalStateException(
                    "Cannot construct ObjectInputStream to wrap ByteArrayInputStream containing " + data.length
                            + " bytes!", e );
        }
        catch ( final ClassNotFoundException e )
        {
            throw new IllegalStateException( "Cannot read ViewParams. A class was missing: " + e.getMessage(), e );
        }
        finally
        {
            IOUtils.closeQuietly( ois );
        }
    }

    public static boolean isCycleDetectionPending( final Node viewNode )
    {
        return getBooleanProperty( CYCLE_DETECTION_PENDING, viewNode, Boolean.FALSE );
    }

    public static void setCycleDetectionPending( final Node viewNode, final boolean pending )
    {
        viewNode.setProperty( CYCLE_DETECTION_PENDING, pending );
    }

    public static boolean isMembershipDetectionPending( final Node viewNode )
    {
        return getBooleanProperty( MEMBERSHIP_DETECTION_PENDING, viewNode, Boolean.FALSE );
    }

    public static void setMembershipDetectionPending( final Node viewNode, final boolean pending )
    {
        viewNode.setProperty( MEMBERSHIP_DETECTION_PENDING, pending );
    }

    private static final String SELECTION_ORIGIN_PREFIX = "_selection_origin_";

    private static final String DESELECTION_TARGET_PREFIX = "_deselection_target_";

    public static final String ATLAS_RELATIONSHIP_COUNT = "_atlas_relationship_count";

    public static final String ATLAS_RELATIONSHIP_INDEX = "_atlas_relationship_index";

    public static long getDeselectionTarget( final long originRid, final Node viewNode )
    {
        return getLongProperty( DESELECTION_TARGET_PREFIX + originRid, viewNode, -1 );
    }

    public static long getSelectionOrigin( final long targetRid, final Node viewNode )
    {
        return getLongProperty( SELECTION_ORIGIN_PREFIX + targetRid, viewNode, -1 );
    }

    public static void setSelection( final long originRid, final long targetRid, final Node viewNode )
    {
        Logger logger = LoggerFactory.getLogger( Conversions.class );
        logger.info( "Setting selection. Deselecting: {} in favor of: {} (view-node: {})", originRid, targetRid,
                     viewNode );
        viewNode.setProperty( DESELECTION_TARGET_PREFIX + originRid, targetRid );
        viewNode.setProperty( SELECTION_ORIGIN_PREFIX + targetRid, originRid );
    }

    public static void removeSelectionByTarget( final long targetRid, final Node viewNode )
    {
        final String selKey = SELECTION_ORIGIN_PREFIX + targetRid;
        final long originRid = getLongProperty( selKey, viewNode, -1 );

        if ( originRid > -1 )
        {
            viewNode.removeProperty( selKey );
        }
        else
        {
            return;
        }

        final String deKey = DESELECTION_TARGET_PREFIX + originRid;
        if ( viewNode.hasProperty( deKey ) )
        {
            viewNode.removeProperty( deKey );
        }
    }

    public static void removeSelectionByOrigin( final long originRid, final Node viewNode )
    {
        final String deKey = DESELECTION_TARGET_PREFIX + originRid;
        final long targetRid = getLongProperty( deKey, viewNode, -1 );
        if ( targetRid > -1 )
        {
            viewNode.removeProperty( deKey );
        }
        else
        {
            return;
        }

        final String selKey = SELECTION_ORIGIN_PREFIX + targetRid;
        if ( viewNode.hasProperty( selKey ) )
        {
            viewNode.removeProperty( selKey );
        }
    }

    public static void storeError( final Node node, final String error )
    {
        node.setProperty( PROJECT_ERROR, error );
    }

    public static String getError( final Node node )
    {
        if ( !node.hasProperty( PROJECT_ERROR ) )
        {
            return null;
        }

        return (String) node.getProperty( PROJECT_ERROR );
    }

}
