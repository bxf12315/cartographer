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
package org.commonjava.cartographer.tck.graph.traverse.ancestry;

import org.commonjava.cartographer.graph.RelationshipGraph;
import org.commonjava.maven.atlas.graph.rel.SimpleParentRelationship;
import org.commonjava.cartographer.graph.traverse.AncestryTraversal;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.cartographer.tck.graph.AbstractSPI_TCK;
import org.junit.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class AncestryTraversal_TwoAncestors_TCK
    extends AbstractSPI_TCK
{

    @Test
    public void run()
        throws Exception
    {
        final ProjectVersionRef myRef = new SimpleProjectVersionRef( "my.group", "my-artifact", "1.0" );
        final ProjectVersionRef parentRef = new SimpleProjectVersionRef( "my.group", "my-dad", "1" );
        final ProjectVersionRef grandRef = new SimpleProjectVersionRef( "other.group", "grandpa", "20120821" );

        final URI source = sourceURI();
        final RelationshipGraph graph = simpleGraph( myRef );

        graph.storeRelationships( new SimpleParentRelationship( source, myRef, parentRef ),
                                  new SimpleParentRelationship( source, parentRef, grandRef ),
                                  new SimpleParentRelationship( grandRef ) );

        final Set<ProjectVersionRef> projects = graph.getAllProjects();
        assertThat( projects.size(), equalTo( 3 ) );
        assertThat( projects.contains( myRef ), equalTo( true ) );
        assertThat( projects.contains( parentRef ), equalTo( true ) );
        assertThat( projects.contains( grandRef ), equalTo( true ) );

        final AncestryTraversal ancestry = new AncestryTraversal();
        graph.traverse( ancestry );

        final List<ProjectVersionRef> ancestorRefs = ancestry.getAncestry();

        logger.info( "Ancestry: {}", ancestorRefs );

        assertThat( ancestorRefs.size(), equalTo( 3 ) );

        int idx = 0;
        ProjectVersionRef ref = ancestorRefs.get( idx++ );

        assertThat( ref.getGroupId(), equalTo( "my.group" ) );
        assertThat( ref.getArtifactId(), equalTo( "my-artifact" ) );

        ref = ancestorRefs.get( idx++ );

        assertThat( ref.getGroupId(), equalTo( "my.group" ) );
        assertThat( ref.getArtifactId(), equalTo( "my-dad" ) );

        ref = ancestorRefs.get( idx++ );

        assertThat( ref.getGroupId(), equalTo( "other.group" ) );
        assertThat( ref.getArtifactId(), equalTo( "grandpa" ) );

    }

}
