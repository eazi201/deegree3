//$HeadURL$
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2009 by:
 - Department of Geography, University of Bonn -
 and
 - lat/lon GmbH -

 This library is free software; you can redistribute it and/or modify it under
 the terms of the GNU Lesser General Public License as published by the Free
 Software Foundation; either version 2.1 of the License, or (at your option)
 any later version.
 This library is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 details.
 You should have received a copy of the GNU Lesser General Public License
 along with this library; if not, write to the Free Software Foundation, Inc.,
 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

 Contact information:

 lat/lon GmbH
 Aennchenstr. 19, 53177 Bonn
 Germany
 http://lat-lon.de/

 Department of Geography, University of Bonn
 Prof. Dr. Klaus Greve
 Postfach 1147, 53001 Bonn
 Germany
 http://www.geographie.uni-bonn.de/deegree/

 e-mail: info@deegree.org
 ----------------------------------------------------------------------------*/
package org.deegree.feature.persistence.mapping.property;

import org.deegree.feature.persistence.mapping.JoinChain;
import org.deegree.feature.types.FeatureType;
import org.deegree.filter.expression.PropertyName;
import org.deegree.filter.sql.MappingExpression;

/**
 * Defines the mapping of a particle of a {@link FeatureType} to a relational model (tables, columns).
 * 
 * @author <a href="mailto:schneider@lat-lon.de">Markus Schneider</a>
 * @author last edited by: $Author$
 * 
 * @version $Revision$, $Date$
 */
public abstract class Mapping {

    private final PropertyName path;

    private final MappingExpression mapping;

    private final JoinChain joinedTable;

    protected Mapping( PropertyName path, MappingExpression mapping, JoinChain joinedTable ) {
        this.path = path;
        this.mapping = mapping;
        this.joinedTable = joinedTable;
    }

    public PropertyName getPath() {
        return path;
    }

    public MappingExpression getMapping() {
        return mapping;
    }

    public JoinChain getJoinedTable() {
        return joinedTable;
    }

    @Override
    public String toString() {
        return "{path=" + path + ",mapping=" + mapping + "}";
    }
}