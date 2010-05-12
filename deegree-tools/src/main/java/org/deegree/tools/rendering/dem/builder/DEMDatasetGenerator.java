//$HeadURL: svn+ssh://mschneider@svn.wald.intevation.org/deegree/base/trunk/resources/eclipse/files_template.xml $
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2009 by:
 Department of Geography, University of Bonn
 and
 lat/lon GmbH

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

package org.deegree.tools.rendering.dem.builder;

import static java.lang.System.currentTimeMillis;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Blob;
import java.sql.SQLException;

import javax.vecmath.Point2f;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.deegree.commons.utils.LogUtils;
import org.deegree.commons.utils.math.MathUtils;
import org.deegree.coverage.AbstractCoverage;
import org.deegree.coverage.raster.AbstractRaster;
import org.deegree.coverage.raster.cache.RasterCache;
import org.deegree.coverage.raster.data.RasterData;
import org.deegree.coverage.raster.data.RasterDataFactory;
import org.deegree.coverage.raster.data.TiledRasterData;
import org.deegree.coverage.raster.data.info.DataType;
import org.deegree.coverage.raster.data.info.RasterDataInfo;
import org.deegree.coverage.raster.geom.RasterGeoReference;
import org.deegree.coverage.raster.geom.RasterRect;
import org.deegree.coverage.raster.geom.RasterGeoReference.OriginLocation;
import org.deegree.coverage.raster.io.RasterIOOptions;
import org.deegree.coverage.raster.io.grid.GridFileReader;
import org.deegree.coverage.raster.io.grid.GridReader;
import org.deegree.coverage.raster.io.grid.GridWriter;
import org.deegree.coverage.raster.utils.Rasters;
import org.deegree.geometry.Envelope;
import org.deegree.rendering.r3d.multiresolution.MultiresolutionMesh;
import org.deegree.tools.CommandUtils;
import org.deegree.tools.annotations.Tool;
import org.deegree.tools.coverage.utils.RasterOptionsParser;
import org.deegree.tools.i18n.Messages;
import org.deegree.tools.rendering.dem.builder.dag.DAGBuilder;

/**
 * Tool for generating the binary files for {@link MultiresolutionMesh} instances (MRIndex- and PatchData-BLOBs) from
 * regular heightfields.
 * <p>
 * The input heightfield file must contain the raw height information as a sequence of height values (heixels).
 * Contained in a raster.
 * <p>
 * Initially, the domain is divided into two right triangles. These are the root fragments of the multiresolution
 * hierarchy. Smaller fragments (with more detail) are generated by recursively bisecting the triangles.
 * 
 * @author <a href="mailto:schneider@lat-lon.de">Markus Schneider</a>
 * @author last edited by: $Author: schneider $
 * 
 * @version $Revision: $, $Date: $
 */
@Tool("Generates DEM multiresolution datasets from rasters, suitable for the WPVS.")
public class DEMDatasetGenerator {

    /*
     * Command line options
     */
    private static final String OPT_VERBOSE = "verbose";

    private static final String OPT_OUTPUT_DIR = "out_dir";

    private static final String OPT_OUTPUT_LEVELS = "out_levels";

    private static final String OPT_OUTPUT_ROWS = "out_rows";

    private static final String OPT_MAX_HEIGHT = "max_z";

    final RasterData dataBuffer;

    /** Number of points in the heixelBuffer (x-dimension). */
    final int inputX;

    /** Number of points in the heixelBuffer (y-dimension). */
    final int inputY;

    private final int levels;

    private final int rowsPerFragment;

    /** Number of points in the domain (x-dimension). */
    private final int outputX;

    /** Number of points in the domain (y-dimension). */
    private final int outputY;

    /** Maximum height value (everything higher is clipped to default value). */
    private final float maxZ;

    private final int verticesPerFragment;

    private final int trianglesPerFragment;

    private final int bytesPerTile;

    private RasterGeoReference geoReference;

    private double tileHeight;

    private final long fileSize;

    // currently fixed to 2 bytes
    private final static int bytesPerTriangleIndexValue = 2;

    private static final int TILE_SIZE = 1000;

    /**
     * Creates a new <code>PatchGenerator</code> instance.
     * 
     * @param raster
     *            the dem raster
     * @param options
     *            containing information on the given raster.
     * @param levels
     *            number of levels in the generated (layered) DAG
     * @param rowsPerTile
     *            number of rows per macro triangle (tile)
     * @param maxZ
     *            the clipping z value.
     * @throws SQLException
     * @throws IOException
     */
    public DEMDatasetGenerator( AbstractRaster raster, RasterIOOptions options, int levels, int rowsPerTile, float maxZ )
                            throws SQLException, IOException {

        this.dataBuffer = buildGrid( raster, options );

        if ( Float.isNaN( maxZ ) ) {
            this.maxZ = getAsFloatSample( -1, -1, 0 );
            System.out.println( "Setting max height value to no data value: " + this.maxZ );
        } else {
            this.maxZ = maxZ;
        }

        this.inputX = dataBuffer.getWidth();
        this.inputY = dataBuffer.getHeight();

        this.rowsPerFragment = rowsPerTile;

        // calculate the best size
        int samplesSize = Math.max( dataBuffer.getWidth(), dataBuffer.getHeight() );
        int nextPowerOfTwo = MathUtils.nextPowerOfTwoValue( samplesSize );

        this.outputX = nextPowerOfTwo;
        this.outputY = nextPowerOfTwo;

        Envelope env = raster.getRasterReference().getEnvelope( new RasterRect( 0, 0, outputX, outputY ), null );
        RasterGeoReference rRef = raster.getRasterReference();
        this.geoReference = new RasterGeoReference( OriginLocation.CENTER, rRef.getResolutionX(),
                                                    rRef.getResolutionY(), rRef.getRotationX(), rRef.getRotationY(), 0,
                                                    raster.getEnvelope().getSpan1(), raster.getCoordinateSystem() );

        Point2f p0 = new Point2f( 0, (float) env.getSpan1() );
        Point2f p1 = new Point2f( 0, 0 );
        Point2f p2 = new Point2f( (float) env.getSpan0(), (float) env.getSpan1() );

        int lowestLevel = MathUtils.previousPowerOfTwo( rowsPerTile );
        int heightesLevel = MathUtils.previousPowerOfTwo( inputX );
        int tL = ( heightesLevel - lowestLevel ) * 2;
        if ( levels == -1 ) {
            System.out.println( "Setting number of levels for " + rowsPerTile + " rows per macro triangle to: " + tL );
            this.levels = tL;
        } else {
            this.levels = levels;
        }
        if ( tL != this.levels ) {
            System.out.println( "++++WARN++++\nThe best number of levels (fitting your data) for " + rowsPerTile
                                + " rows per macro triangle should be: " + tL + ". You provided: " + levels
                                + ", this will result in " + ( ( levels < tL ) ? "under" : "over" )
                                + " sampling your data.\n++++++++++++" );
        }

        /**
         * rb: a macro triangle will consist of all inner vertices + the 'half-way' vertices which make sure that two
         * triangles will fit together. for example: <code>
         * rowsPerTile = 2; 
         * verticersPerTile = 4 * 4 - 3 = 13 
         * (dots are vertices)
         *     .
         *   :/.\:
         *  :/...\:
         *  </code>
         * 
         */

        this.verticesPerFragment = ( rowsPerTile + 2 ) * ( rowsPerTile + 2 ) - 3;

        // rb: draw it out, it is working.
        this.trianglesPerFragment = ( 4 * rowsPerTile ) + ( 2 * ( rowsPerTile - 1 ) * rowsPerTile );

        if ( getVerticesPerFragment() > 65536 ) {
            throw new RuntimeException( Messages.getMessage( "DEMDSGEN_TOO_MANY_VERTICES" ) );
        }

        int bytesPerMacroTrinagle = ( 4 + 4 * 3 * getVerticesPerFragment() + bytesPerTriangleIndexValue * 3
                                                                             * getTrianglesPerFragment() );

        // normal vectors
        this.bytesPerTile = ( bytesPerMacroTrinagle + ( 4 * 3 * getVerticesPerFragment() ) );

        long fs = 0;
        int level = 0;
        while ( level < this.levels ) {
            fs += bytesPerTile * ( 2l << level++ );
        }
        fileSize = fs;

        double minX = raster.getEnvelope().getMin().get0();
        double minY = raster.getEnvelope().getMin().get1();

        System.out.println( "\nInitializing DEMDatasetGenerator" );
        System.out.println( "--------------------------------\n" );
        // System.out.println( "- input file: " + inputFileName );
        System.out.println( "- bintritree levels: " + levels );
        System.out.println( "- rows per tile: " + rowsPerTile );
        System.out.println( "- vertices per tile: " + getVerticesPerFragment() );
        System.out.println( "- triangles per tile: " + getTrianglesPerFragment() );
        System.out.println( "- bytes per tile: " + getBytesPerTile() );
        System.out.println( "- filesize will be: " + fileSize + " bytes ("
                            + Math.round( ( fileSize / ( 1024 * 1024d ) ) * 100d ) / 100d + " Mb)" );
        System.out.println( "- WPVS translationvector should be: <TranslationToLocalCRS x=\"-" + minX + "\" y=\"-"
                            + minY + "\"/>" );

        outputTriangleHeights( p0, p1, p2, this.getLevels() );

    }

    /**
     * @return the verticesPerFragment
     */
    public int getVerticesPerFragment() {
        return verticesPerFragment;
    }

    /**
     * @return the trianglesPerFragment
     */
    public int getTrianglesPerFragment() {
        return trianglesPerFragment;
    }

    /**
     * @return the levels
     */
    public int getLevels() {
        return levels;
    }

    /**
     * @return the rowsPerFragment
     */
    public int getRowsPerFragment() {
        return rowsPerFragment;
    }

    /**
     * @param tileHeight
     *            the tileHeight to set
     */
    public void setTileHeight( double tileHeight ) {
        this.tileHeight = tileHeight;
    }

    /**
     * @return the tileHeight
     */
    public double getTileHeight() {
        return tileHeight;
    }

    /**
     * @return the bytesPerTile
     */
    public int getBytesPerTile() {
        return bytesPerTile;
    }

    /**
     * 
     */
    private void outputTriangleHeights( Point2f p0, Point2f p1, Point2f p2, int level ) {
        Point2f midPoint = calcMidPoint( p1, p2 );
        if ( level > 0 ) {
            System.out.println( "At level " + level + " each macro triangle will have a height of: "
                                + ( p0.distance( midPoint ) / this.geoReference.getResolutionX() ) + " meters." );
            outputTriangleHeights( midPoint, p0, p1, level - 1 );
        }
    }

    /**
     * @param rast
     * @return
     * @throws IOException
     */
    private TiledRasterData buildGrid( AbstractRaster raster, RasterIOOptions options )
                            throws IOException {

        RasterGeoReference rasterReference = raster.getRasterReference().createRelocatedReference( OriginLocation.OUTER );
        Envelope renv = raster.getRasterReference().relocateEnvelope( OriginLocation.OUTER, raster.getEnvelope() );
        // calculate the rows.
        RasterRect rect = rasterReference.convertEnvelopeToRasterCRS( raster.getEnvelope() );

        RasterCache.dispose();

        int numberOfTiles = Rasters.calcApproxTiles( rect.width, rect.height, TILE_SIZE );
        int tileWidth = Rasters.calcTileSize( rect.width, numberOfTiles );
        int tileHeight = Rasters.calcTileSize( rect.height, numberOfTiles );
        int columns = (int) Math.ceil( ( (double) rect.width ) / tileWidth );
        int rows = (int) Math.ceil( (double) rect.height / tileHeight );
        //

        RasterDataInfo inf = raster.getRasterDataInfo();
        long filesize = ( (long) rows ) * columns * ( tileHeight * tileWidth * inf.dataSize * inf.bands );
        this.setTileHeight( rasterReference.getEnvelope( new RasterRect( 0, 0, tileWidth, tileHeight ), null ).getSpan1() );
        String cD = options.get( RasterIOOptions.RASTER_CACHE_DIR );
        File cacheDir = ( cD == null ) ? RasterCache.DEFAULT_CACHE_DIR : new File( cD );
        File heixelFile = new File( cacheDir, "heixel.grid" );
        boolean createNew = !heixelFile.exists();
        if ( !createNew ) {
            // calculate the size of the heixel file.
            long length = heixelFile.length();
            createNew = length != filesize;
            if ( !createNew ) {
                System.out.println( "Found a temporary storage of your data (heixel.grid) at location: "
                                    + heixelFile.getAbsolutePath() + " with correct size: " + filesize + " bytes (ca. "
                                    + ( Math.round( ( filesize / ( 1024 * 1024d ) ) * 100 ) * 0.01 )
                                    + " Mb). It will be used for the creation of your Multiresolution mesh." );

            }
        }

        GridReader reader = null;

        if ( createNew ) {
            System.out.println( "Creating grid of " + rows + "x" + columns + " (rows x columns) and " + rect.width
                                + "x" + rect.height + " samples (width x height), each tile will have " + tileWidth
                                + "x" + tileHeight + " samples (width x height), resulting file will have " + filesize
                                + " bytes (ca. " + ( Math.round( ( filesize / ( 1024 * 1024d ) ) * 100 ) * 0.01 )
                                + " Mb)" );
            try {
                GridWriter writer = new GridWriter( columns, rows, renv, rasterReference, heixelFile, inf );
                writer.write( raster, options );
            } catch ( IOException e ) {
                e.printStackTrace();
            }
        }
        reader = new GridFileReader( heixelFile, options );
        return RasterDataFactory.createTiledRasterData( reader, options );
    }

    private PatchManager generateMacroTriangles( PatchManager triangleManager, float minX, float minY, float maxX,
                                                 float maxY ) {

        Point2f p0 = new Point2f( minX, maxY );
        Point2f p1 = new Point2f( minX, minY );
        Point2f p2 = new Point2f( maxX, maxY );
        Point2f p3 = new Point2f( maxX, minY );

        // prepare workers
        Worker worker1 = new Worker( this, triangleManager, "0", p0, p1, p2 );
        Worker worker2 = new Worker( this, triangleManager, "1", p3, p2, p1 );

        long sT = currentTimeMillis();
        // start workers in different threads
        Thread t = new Thread( worker1, "Upper left macro triangle" );
        t.start();
        Thread.currentThread().setName( "Lower right macro triangle" );
        worker2.run();

        // wait indefinitely for thread t to finish
        try {
            t.join();
            // Finished
        } catch ( InterruptedException e ) {
            // Thread was interrupted
            e.printStackTrace();
        }
        System.out.println( LogUtils.createDurationTimeString( "Creation of triangles", sT, true ) );

        return triangleManager;
    }

    float getHeight( float x, float y ) {
        int[] rasterCoordinate = this.geoReference.getRasterCoordinate( x, y );
        float height = getAsFloatSample( rasterCoordinate[0], rasterCoordinate[1], 0 );
        if ( height > maxZ ) {
            height = maxZ;
        }
        return height;
    }

    /**
     * @param i
     * @param j
     * @param k
     */
    private float getAsFloatSample( int rasterX, int rasterY, int band ) {
        DataType dataType = this.dataBuffer.getDataType();
        switch ( dataType ) {
        case BYTE:
            return this.dataBuffer.getByteSample( rasterX, rasterY, band );
        case DOUBLE:
            return (float) this.dataBuffer.getDoubleSample( rasterX, rasterY, band );
        case FLOAT:
            return this.dataBuffer.getFloatSample( rasterX, rasterY, band );
        case INT:
            return this.dataBuffer.getIntSample( rasterX, rasterY, band );
        case SHORT:
            return this.dataBuffer.getShortSample( rasterX, rasterY, band );
        case UNDEFINED:
            throw new IllegalArgumentException( "Unknown Data type, this cannot be." );
        case USHORT:
            return this.dataBuffer.getShortSample( rasterX, rasterY, band ) & 0xffff;
        }
        throw new IllegalArgumentException( "Unknown Data type, this cannot be." );
    }

    float getHeight( Point2f p ) {
        return getHeight( p.x, p.y );
    }

    Point2f calcMidPoint( Point2f pa, Point2f pb ) {
        float minX = Math.min( pb.x, pa.x );
        float minY = Math.min( pb.y, pa.y );
        float midX = ( Math.abs( pb.x - pa.x ) * 0.5f ) + minX;
        float midY = ( Math.abs( pb.y - pa.y ) * 0.5f ) + minY;
        return new Point2f( midX, midY );
    }

    private class Worker implements Runnable {

        private DEMDatasetGenerator builder;

        private String startLocationCode;

        private Point2f p0, p1, p2;

        private Point3f[] tileVertices;

        private Vector3f[] vertexNormals;

        private int[][] tileTriangles;

        private PatchManager triangleManager;

        Worker( DEMDatasetGenerator builder, PatchManager triangleManager, String startLocationCode, Point2f p0,
                Point2f p1, Point2f p2 ) {
            this.builder = builder;
            this.triangleManager = triangleManager;
            this.startLocationCode = startLocationCode;
            this.p0 = p0;
            this.p1 = p1;
            this.p2 = p2;
            this.tileVertices = new Point3f[getVerticesPerFragment()];
            this.vertexNormals = new Vector3f[getVerticesPerFragment()];
            this.tileTriangles = new int[getTrianglesPerFragment()][3];
        }

        public void run() {
            String tria = startLocationCode.equals( "0" ) ? "Lower right" : "Upper left";
            System.out.println( "Starting a worker at location code " + startLocationCode + " (" + tria
                                + " macro triangle)." );
            createTriangleTree( p0, p1, p2, getLevels() - startLocationCode.length() + 1, startLocationCode );
        }

        private int builtTriangles = 0;

        /**
         * Recursive method creates the triangles from the given points, which are in world coordinates.
         * 
         * @param p0
         * @param p1
         * @param p2
         * @param level
         * @param locationCode
         * @return
         */
        private float[][] createTriangleTree( Point2f p0, Point2f p1, Point2f p2, int level, String locationCode ) {

            float error = estimateError( p0, p1, p2 );
            Point2f midPoint = calcMidPoint( p1, p2 );
            MacroTriangle triangle = null;
            if ( level > 1 ) {
                // generate deeper levels first (in order to enable bottom-up bbox propagation)
                long time = -1;
                if ( !( locationCode.substring( 1 ) ).contains( "1" ) ) {
                    time = System.currentTimeMillis();
                }

                float[][] bbox1 = createTriangleTree( midPoint, p0, p1, level - 1, locationCode + "0" );
                float[][] bbox2 = createTriangleTree( midPoint, p2, p0, level - 1, locationCode + "1" );
                float[][] bbox = mergeBBoxes( bbox1, bbox2 );
                // long time = System.currentTimeMillis();

                triangle = new MacroTriangle( builder, p0, p1, p2, level, locationCode, bbox, error );
                if ( !( locationCode.substring( 1 ) ).contains( "1" ) ) {
                    String message = Thread.currentThread().getName() + " finished creation of level: " + level + " ("
                                     + locationCode + ")";
                    System.out.println( LogUtils.createDurationTimeString( message, time, true ) );

                }

                builtTriangles++;
            } else {
                if ( level < 1 ) {
                    System.err.println( "The level is smaller than 1, this may not be!" );
                }
                triangle = new MacroTriangle( builder, p0, p1, p2, level, locationCode, error );
                builtTriangles++;
            }

            try {
                storeMacroTriangle( triangle );
            } catch ( Exception e ) {
                e.printStackTrace();
            }
            return triangle.getBBox();
        }

        private float estimateError( Point2f p0, Point2f p1, Point2f p2 ) {
            // ms: TODO implement a real error calculation
            Point2f midPoint = calcMidPoint( p1, p2 );
            float dist = p0.distance( midPoint );
            float heixelDist = dist / getRowsPerFragment();
            return heixelDist;
        }

        private float[][] mergeBBoxes( float[][] bbox1, float[][] bbox2 ) {
            float[][] bbox = new float[][] { new float[] { bbox1[0][0], bbox1[0][1], bbox1[0][2] },
                                            new float[] { bbox1[1][0], bbox1[1][1], bbox1[1][2] } };
            for ( int i = 0; i <= 2; i++ ) {
                if ( bbox[0][i] > bbox2[0][i] ) {
                    bbox[0][i] = bbox2[0][i];
                }
            }
            for ( int i = 0; i <= 2; i++ ) {
                if ( bbox[1][i] < bbox2[1][i] ) {
                    bbox[1][i] = bbox2[1][i];
                }
            }
            return bbox;
        }

        private void storeMacroTriangle( MacroTriangle tile )
                                throws SQLException {
            tile.generateTileData( dataBuffer, getTileHeight(), getRowsPerFragment(), tileVertices, vertexNormals,
                                   tileTriangles );

            ByteBuffer rawTileBuffer = ByteBuffer.allocate( getBytesPerTile() );
            rawTileBuffer.order( ByteOrder.nativeOrder() );

            // store number of vertices
            rawTileBuffer.putInt( tileVertices.length );

            // store vertices
            int i = 0;
            int pos = 0;
            for ( Point3f vertex : tileVertices ) {
                rawTileBuffer.putFloat( vertex.x );
                pos += 4;
                rawTileBuffer.putFloat( vertex.y );
                pos += 4;
                rawTileBuffer.putFloat( vertex.z );
                pos += 4;
                i++;
            }

            // store normals
            for ( Vector3f normal : vertexNormals ) {
                rawTileBuffer.putFloat( normal.x );
                rawTileBuffer.putFloat( normal.y );
                rawTileBuffer.putFloat( normal.z );
            }

            // store triangles (as vertex indexes)
            for ( int[] triangle : tileTriangles ) {
                rawTileBuffer.putShort( (short) triangle[0] );
                rawTileBuffer.putShort( (short) triangle[1] );
                rawTileBuffer.putShort( (short) triangle[2] );
            }
            triangleManager.storePatch( tile, rawTileBuffer );
        }
    }

    /**
     * Generates a {@link MultiresolutionMesh} instance (MRIndex- and PatchData-BLOBs) from a file that contains binary
     * short values (a regular heightfield).
     * <p>
     * Please see the code for the initialization of the parameters.
     * 
     * @param args
     */
    public static void main( String[] args ) {
        CommandLineParser parser = new PosixParser();

        Options options = initOptions();
        boolean verbose = false;

        // for the moment, using the CLI API there is no way to respond to a help argument; see
        // https://issues.apache.org/jira/browse/CLI-179
        if ( args != null && args.length > 0 ) {
            for ( String a : args ) {
                if ( a != null && a.toLowerCase().contains( "help" ) || "-?".equals( a ) ) {
                    printHelp( options );
                }
            }
        }
        CommandLine line = null;
        try {
            line = parser.parse( options, args );
            verbose = line.hasOption( OPT_VERBOSE );
            init( line );
        } catch ( ParseException exp ) {
            System.err.println( "ERROR: Invalid command line: " + exp.getMessage() );
            printHelp( options );
        } catch ( Throwable e ) {
            System.err.println( "An Exception occurred while creating a Multiresolution mesh from your data, error message: "
                                + e.getMessage() );
            if ( verbose ) {
                e.printStackTrace();
            }
            System.exit( 1 );
        }
    }

    private static void init( CommandLine line )
                            throws ParseException, IOException, SQLException {

        String t = line.getOptionValue( OPT_OUTPUT_LEVELS, "-1" );
        int levels = Integer.parseInt( t );
        t = line.getOptionValue( OPT_OUTPUT_ROWS, "128" );
        int rows = Integer.parseInt( t );
        t = line.getOptionValue( OPT_MAX_HEIGHT );
        float maxZ = Float.NaN;
        if ( t != null ) {
            maxZ = Float.parseFloat( t );
        }

        RasterIOOptions rasterIOOptions = RasterOptionsParser.parseRasterIOOptions( line );
        AbstractCoverage raster = RasterOptionsParser.loadCoverage( line, rasterIOOptions );
        if ( !( raster instanceof AbstractRaster ) ) {
            throw new IllegalArgumentException(
                                                "Given raster location holds a multiresolution raster, building a multi resolution mesh from this coverage is not supported." );
        }

        DEMDatasetGenerator builder = new DEMDatasetGenerator( (AbstractRaster) raster, rasterIOOptions, levels, rows,
                                                               maxZ );

        t = line.getOptionValue( OPT_OUTPUT_DIR );
        File outputDir = new File( t );
        if ( outputDir.getFreeSpace() < builder.fileSize ) {
            System.err.println( "Not enough space (" + outputDir.getFreeSpace() + " bytes ca: "
                                + ( Math.round( ( outputDir.getFreeSpace() / ( 1024 * 1024d ) ) * 100d ) / 100d )
                                + " Mb.) free in the directory: " + outputDir
                                + " please specify a location where at least: " + builder.fileSize + " bytes (ca. "
                                + ( Math.round( ( builder.fileSize / ( 1024 * 1024d ) ) * 100d ) / 100d )
                                + " Mb) are available." );
            System.exit( 2 );
        }
        Blob patchesBlob = new FileBlob( new File( outputDir, MultiresolutionMesh.FRAGMENTS_FILE_NAME ) );

        PatchManager triangleManager = new PatchManager( builder.getLevels(), patchesBlob );
        System.out.println( triangleManager );

        // generate macro triangle blob
        PatchManager manager = builder.generateMacroTriangles( triangleManager, 0, 0, builder.outputX, builder.outputY );

        // write mrindex blob
        Blob mrIndexBlob = new FileBlob( new File( outputDir, MultiresolutionMesh.INDEX_FILE_NAME ) );
        DAGBuilder dagBuilder = new DAGBuilder( manager.getLevels(), manager );
        dagBuilder.writeBlob( mrIndexBlob );
        dagBuilder.printStats();
        mrIndexBlob.free();
    }

    private static Options initOptions() {

        Options opts = new Options();

        RasterOptionsParser.addRasterIOLineOptions( opts );

        Option opt = new Option( "o", OPT_OUTPUT_DIR, true, "output directory" );
        opt.setRequired( true );
        opts.addOption( opt );

        opt = new Option( "ol", OPT_OUTPUT_LEVELS, true,
                          "number of resolution levels in the generated multiresolution model." );
        opts.addOption( opt );

        opt = new Option( "or", OPT_OUTPUT_ROWS, true, "number of rows per macrotriangle" );
        opt.setDescription( "32|64|128" );
        opts.addOption( opt );

        opt = new Option( "mh", OPT_MAX_HEIGHT, true, "maximum z-value, every higher value is clipped to no data value" );
        opts.addOption( opt );

        opts.addOption( "?", "help", false, "print (this) usage information" );
        opts.addOption( "v", OPT_VERBOSE, false, "be verbose on error." );

        return opts;
    }

    private static void printHelp( Options options ) {
        CommandUtils.printHelp( options, DEMDatasetGenerator.class.getSimpleName(), null, "outputdir" );
    }
}
