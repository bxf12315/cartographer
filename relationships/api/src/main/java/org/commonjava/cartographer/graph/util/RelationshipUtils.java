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
package org.commonjava.cartographer.graph.util;

import static org.commonjava.maven.atlas.graph.rel.RelationshipConstants.POM_ROOT_URI;
import static org.commonjava.maven.atlas.ident.util.IdentityUtils.artifact;
import static org.commonjava.maven.atlas.ident.util.IdentityUtils.projectVersion;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.commonjava.cartographer.graph.filter.AbstractAggregatingFilter;
import org.commonjava.cartographer.graph.filter.AbstractTypedFilter;
import org.commonjava.cartographer.graph.filter.AnyFilter;
import org.commonjava.cartographer.graph.filter.ProjectRelationshipFilter;
import org.commonjava.maven.atlas.graph.rel.*;
import org.commonjava.maven.atlas.ident.DependencyScope;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.util.JoinString;
import org.commonjava.maven.atlas.ident.version.InvalidVersionSpecificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RelationshipUtils
{

    private RelationshipUtils()
    {
    }

    public static boolean isExcluded( final ProjectRef ref, final Collection<ProjectRef> excludes )
    {
        if ( excludes == null || excludes.isEmpty() )
        {
            return false;
        }

        for ( final ProjectRef ex : excludes )
        {
            if ( ex == null )
            {
                continue;
            }

            if ( ex.matches( ref ) )
            {
                return true;
            }
        }

        return false;
    }

    public static Map<ProjectVersionRef, List<ProjectRelationship<?, ?>>> mapByDeclaring( final Collection<? extends ProjectRelationship<?, ?>> relationships )
    {
        final Logger logger = LoggerFactory.getLogger( RelationshipUtils.class );
        logger.debug( "Mapping {} relationships by declaring GAV:\n\n  {}\n\n", relationships.size(), new JoinString( "\n  ", relationships ) );
        final Map<ProjectVersionRef, List<ProjectRelationship<?, ?>>> result = new HashMap<ProjectVersionRef, List<ProjectRelationship<?, ?>>>();
        for ( final ProjectRelationship<?, ?> rel : relationships )
        {
            final ProjectVersionRef declaring = rel.getDeclaring();
            List<ProjectRelationship<?, ?>> outbound = result.get( declaring );
            if ( outbound == null )
            {
                outbound = new ArrayList<ProjectRelationship<?, ?>>();
                result.put( rel.getDeclaring(), outbound );
            }

            if ( !outbound.contains( rel ) )
            {
                outbound.add( rel );
            }
        }

        return result;

    }

    public static URI profileLocation( final String profile )
    {
        if ( profile == null || profile.trim()
                                       .length() < 1 )
        {
            return POM_ROOT_URI;
        }

        try
        {
            return new URI( "pom:profile:" + profile );
        }
        catch ( final URISyntaxException e )
        {
            throw new IllegalStateException( "Cannot construct pom-profile URI: 'pom:profile:" + profile + "'" );
        }
    }

    public static void filterTerminalParents( final Collection<? extends ProjectRelationship<?, ?>> rels )
    {
        for ( final Iterator<? extends ProjectRelationship<?, ?>> it = rels.iterator(); it.hasNext(); )
        {
            final ProjectRelationship<?, ?> rel = it.next();
            if ( ( rel instanceof SimpleParentRelationship ) && ( (ParentRelationship) rel ).isTerminus() )
            {
                it.remove();
            }
        }
    }

    public static void filter( final Set<? extends ProjectRelationship<?, ?>> rels, final RelationshipType... types )
    {
        if ( rels == null || rels.isEmpty() )
        {
            return;
        }

        if ( types == null || types.length < 1 )
        {
            return;
        }

        Arrays.sort( types );
        for ( final Iterator<? extends ProjectRelationship<?, ?>> iterator = rels.iterator(); iterator.hasNext(); )
        {
            final ProjectRelationship<?, ?> rel = iterator.next();
            if ( Arrays.binarySearch( types, rel.getType() ) < 0 )
            {
                iterator.remove();
            }
        }
    }

    public static void filter( final Set<? extends ProjectRelationship<?, ?>> rels, final ProjectRelationshipFilter filter )
    {
        if ( filter == null || filter instanceof AnyFilter )
        {
            return;
        }

        if ( rels == null || rels.isEmpty() )
        {
            return;
        }

        for ( final Iterator<? extends ProjectRelationship<?, ?>> iterator = rels.iterator(); iterator.hasNext(); )
        {
            final ProjectRelationship<?, ?> rel = iterator.next();
            if ( !filter.accept( rel ) )
            {
                iterator.remove();
            }
        }
    }

    public static Set<ProjectVersionRef> declarers( final ProjectRelationship<?, ?>... relationships )
    {
        return declarers( Arrays.asList( relationships ) );
    }

    public static Set<ProjectVersionRef> declarers( final Collection<? extends ProjectRelationship<?, ?>> relationships )
    {
        final Set<ProjectVersionRef> results = new HashSet<ProjectVersionRef>();
        for ( final ProjectRelationship<?, ?> rel : relationships )
        {
            results.add( rel.getDeclaring() );
        }

        return results;
    }

    public static Set<ProjectVersionRef> targets( final ProjectRelationship<?, ?>... relationships )
    {
        return targets( Arrays.asList( relationships ) );
    }

    public static Set<ProjectVersionRef> targets( final Collection<? extends ProjectRelationship<?, ?>> relationships )
    {
        if ( relationships == null )
        {
            return null;
        }

        final Set<ProjectVersionRef> results = new HashSet<ProjectVersionRef>();
        for ( final ProjectRelationship<?, ?> rel : relationships )
        {
            results.add( rel.getTarget() );
        }

        return results;
    }

    public static Set<ProjectVersionRef> gavs( final ProjectRelationship<?, ?>... relationships )
    {
        return gavs( Arrays.asList( relationships ) );
    }

    public static Set<ProjectVersionRef> gavs( final Collection<? extends ProjectRelationship<?, ?>> relationships )
    {
        final Set<ProjectVersionRef> results = new HashSet<ProjectVersionRef>();
        for ( final ProjectRelationship<?, ?> rel : relationships )
        {
            results.add( rel.getDeclaring()
                            .asProjectVersionRef() );

            results.add( rel.getTarget()
                            .asProjectVersionRef() );
        }

        return results;
    }

    public static ExtensionRelationship extension( final URI source, final URI pomLocation,
                                                   final ProjectVersionRef owner, final String groupId,
                                                   final String artifactId, final String version, final int index,
                                                   final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return new SimpleExtensionRelationship( source, pomLocation, owner, projectVersion( groupId, artifactId, version ),
                                                index, inherited );
    }

    public static PluginRelationship plugin( final URI source, final URI pomLocation, final ProjectVersionRef owner,
                                             final String groupId, final String artifactId, final String version,
                                             final int index, final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return plugin( source, pomLocation, owner, groupId, artifactId, version, index, false, inherited );
    }

    public static PluginRelationship plugin( final URI source, final URI pomLocation, final ProjectVersionRef owner,
                                             final String groupId, final String artifactId, final String version,
                                             final int index, final boolean managed, final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return new SimplePluginRelationship( source, pomLocation, owner, projectVersion( groupId, artifactId, version ),
                                             index, managed, inherited );
    }

    public static PluginRelationship plugin( final URI source, final URI pomLocation, final ProjectVersionRef owner,
                                             final ProjectVersionRef plugin, final int index, final boolean managed,
                                             final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return new SimplePluginRelationship( source, pomLocation, owner, plugin, index, managed, inherited );
    }

    public static PluginRelationship plugin( final URI source, final ProjectVersionRef owner, final String groupId,
                                             final String artifactId, final String version, final int index,
                                             final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return plugin( source, owner, groupId, artifactId, version, index, false, inherited );
    }

    public static PluginRelationship plugin( final URI source, final ProjectVersionRef owner, final String groupId,
                                             final String artifactId, final String version, final int index,
                                             final boolean managed, final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return new SimplePluginRelationship( source, owner, projectVersion( groupId, artifactId, version ), index,
                                             managed, inherited );
    }

    public static PluginRelationship plugin( final URI source, final ProjectVersionRef owner,
                                             final ProjectVersionRef plugin, final int index, final boolean managed,
                                             final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return new SimplePluginRelationship( source, owner, plugin, index, managed, inherited );
    }

    public static PluginDependencyRelationship pluginDependency( final URI source, final ProjectVersionRef owner,
                                                                 final ProjectRef plugin, final String groupId,
                                                                 final String artifactId, final String version,
                                                                 final int index, final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return pluginDependency( source, owner, plugin, groupId, artifactId, version, null, null, index, false, inherited );
    }

    public static PluginDependencyRelationship pluginDependency( final URI source, final ProjectVersionRef owner, final ProjectRef plugin,
                                                                 final String groupId, final String artifactId, final String version,
                                                                 final int index, final boolean managed, final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return pluginDependency( source, owner, plugin, groupId, artifactId, version, null, null, index, managed, inherited );
    }

    public static PluginDependencyRelationship pluginDependency( final URI source, final ProjectVersionRef owner, final ProjectRef plugin,
                                                                 final String groupId, final String artifactId, final String version,
                                                                 final String type, final String classifier, final int index, final boolean managed,
                                                                 final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return new SimplePluginDependencyRelationship( source, owner, plugin, artifact( groupId, artifactId, version, type, classifier ), index,
                                                       managed, inherited );
    }

    public static PluginDependencyRelationship pluginDependency( final URI source, final ProjectVersionRef owner, final ProjectRef plugin,
                                                                 final ProjectVersionRef dep, final String type, final String classifier,
                                                                 final int index, final boolean managed, final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return new SimplePluginDependencyRelationship( source, owner, plugin, artifact( dep, type, classifier ), index, managed, inherited );
    }

    public static PluginDependencyRelationship pluginDependency( final URI source, final URI pomLocation, final ProjectVersionRef owner,
                                                                 final ProjectRef plugin, final String groupId, final String artifactId,
                                                                 final String version, final int index, final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return pluginDependency( source, pomLocation, owner, plugin, groupId, artifactId, version, null, null, index, false, inherited );
    }

    public static PluginDependencyRelationship pluginDependency( final URI source, final URI pomLocation, final ProjectVersionRef owner,
                                                                 final ProjectRef plugin, final String groupId, final String artifactId,
                                                                 final String version, final int index, final boolean managed,
                                                                 final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return pluginDependency( source, pomLocation, owner, plugin, groupId, artifactId, version, null, null, index, managed, inherited );
    }

    public static PluginDependencyRelationship pluginDependency( final URI source, final URI pomLocation, final ProjectVersionRef owner,
                                                                 final ProjectRef plugin, final String groupId, final String artifactId,
                                                                 final String version, final String type, final String classifier, final int index,
                                                                 final boolean managed, final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return new SimplePluginDependencyRelationship( source, pomLocation, owner, plugin,
                                                 artifact( groupId, artifactId, version, type, classifier ), index, managed, inherited );
    }

    public static PluginDependencyRelationship pluginDependency( final URI source, final URI pomLocation, final ProjectVersionRef owner,
                                                                 final ProjectRef plugin, final ProjectVersionRef dep, final String type,
                                                                 final String classifier, final int index, final boolean managed,
                                                                 final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return new SimplePluginDependencyRelationship( source, pomLocation, owner, plugin, artifact( dep, type, classifier ), index, managed,
                                                       inherited );
    }

    public static DependencyRelationship dependency( final URI source, final ProjectVersionRef owner, final String groupId, final String artifactId,
                                                     final String version, final int index, final boolean inherited, final boolean optional )
        throws InvalidVersionSpecificationException
    {
        return dependency( source, owner, groupId, artifactId, version, null, null, optional, null, index, false, inherited );
    }

    public static DependencyRelationship dependency( final URI source, final ProjectVersionRef owner,
                                                     final ProjectVersionRef dep, final int index, final boolean inherited,
                                                     final boolean optional )
                    throws InvalidVersionSpecificationException
    {
        return dependency( source, owner, dep, null, null, optional, null, index, false, inherited );
    }

    public static DependencyRelationship dependency( final URI source, final ProjectVersionRef owner, final String groupId, final String artifactId,
                                                     final String version, final DependencyScope scope, final int index, final boolean managed,
                                                     final boolean inherited, final boolean optional )
        throws InvalidVersionSpecificationException
    {
        return dependency( source, owner, groupId, artifactId, version, null, null, optional, scope, index, managed, inherited );
    }

    public static DependencyRelationship dependency( final URI source, final ProjectVersionRef owner, final ProjectVersionRef dep,
                                                     final DependencyScope scope, final int index, final boolean managed,
                                                     final boolean inherited, final boolean optional )
        throws InvalidVersionSpecificationException
    {
        return new SimpleDependencyRelationship( source, owner, artifact( dep, null, null ), scope, index, managed, inherited, optional );
    }

    public static DependencyRelationship dependency( final URI source, final ProjectVersionRef owner, final String groupId, final String artifactId,
                                                     final String version, final String type, final String classifier, final boolean optional,
                                                     final DependencyScope scope, final int index, final boolean managed, final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return new SimpleDependencyRelationship( source, owner, artifact( groupId, artifactId, version, type, classifier ), scope, index,
                                                 managed, inherited, optional );
    }

    public static DependencyRelationship dependency( final URI source, final ProjectVersionRef owner, final ProjectVersionRef dep, final String type,
                                                     final String classifier, final boolean optional, final DependencyScope scope, final int index,
                                                     final boolean managed, final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return new SimpleDependencyRelationship( source, owner, artifact( dep, type, classifier ), scope, index, managed, inherited, optional );
    }

    public static DependencyRelationship dependency( final URI source, final URI pomLocation, final ProjectVersionRef owner, final String groupId,
                                                     final String artifactId, final String version, final int index, final boolean inherited,
                                                     final boolean optional )
        throws InvalidVersionSpecificationException
    {
        return dependency( source, pomLocation, owner, groupId, artifactId, version, null, null, optional, null, index, false, inherited );
    }

    public static DependencyRelationship dependency( final URI source, final URI pomLocation, final ProjectVersionRef owner,
                                                     final ProjectVersionRef dep, final int index, final boolean inherited,
                                                     final boolean optional )
        throws InvalidVersionSpecificationException
    {
        return dependency( source, pomLocation, owner, dep, null, null, optional, null, index, false, inherited );
    }

    public static DependencyRelationship dependency( final URI source, final URI pomLocation, final ProjectVersionRef owner, final String groupId,
                                                     final String artifactId, final String version, final DependencyScope scope, final int index,
                                                     final boolean managed, final boolean inherited, final boolean optional )
        throws InvalidVersionSpecificationException
    {
        return dependency( source, pomLocation, owner, groupId, artifactId, version, null, null, optional, scope, index, managed, inherited );
    }

    public static DependencyRelationship dependency( final URI source, final URI pomLocation, final ProjectVersionRef owner,
                                                     final ProjectVersionRef dep, final DependencyScope scope, final int index,
                                                     final boolean managed, final boolean inherited, final boolean optional )
        throws InvalidVersionSpecificationException
    {
        return new SimpleDependencyRelationship( source, pomLocation, owner, artifact( dep, null, null ), scope, index, managed, inherited, optional );
    }

    public static DependencyRelationship dependency( final URI source, final URI pomLocation, final ProjectVersionRef owner, final String groupId,
                                                     final String artifactId, final String version, final String type, final String classifier,
                                                     final boolean optional, final DependencyScope scope, final int index, final boolean managed,
                                                     final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return new SimpleDependencyRelationship( source, pomLocation, owner, artifact( groupId, artifactId, version, type, classifier ), scope,
                                                 index, managed, inherited, optional );
    }

    public static DependencyRelationship dependency( final URI source, final URI pomLocation, final ProjectVersionRef owner,
                                                     final ProjectVersionRef dep, final String type, final String classifier, final boolean optional,
                                                     final DependencyScope scope, final int index, final boolean managed, final boolean inherited )
        throws InvalidVersionSpecificationException
    {
        return new SimpleDependencyRelationship( source, pomLocation, owner, artifact( dep, type, classifier ), scope, index, managed, inherited, optional );
    }

    public static Set<RelationshipType> getRelationshipTypes( final ProjectRelationshipFilter filter )
    {
        if ( filter == null )
        {
            return new HashSet<RelationshipType>( Arrays.asList( RelationshipType.values() ) );
        }

        final Set<RelationshipType> result = new HashSet<RelationshipType>();

        if ( filter instanceof AbstractTypedFilter )
        {
            final AbstractTypedFilter typedFilter = (AbstractTypedFilter) filter;
            result.addAll( typedFilter.getRelationshipTypes() );
            result.addAll( typedFilter.getDescendantRelationshipTypes() );
        }
        else if ( filter instanceof AbstractAggregatingFilter )
        {
            final List<? extends ProjectRelationshipFilter> filters = ( (AbstractAggregatingFilter) filter ).getFilters();

            for ( final ProjectRelationshipFilter f : filters )
            {
                result.addAll( getRelationshipTypes( f ) );
            }
        }
        else
        {
            result.addAll( Arrays.asList( RelationshipType.values() ) );
        }

        return result;
    }

}
