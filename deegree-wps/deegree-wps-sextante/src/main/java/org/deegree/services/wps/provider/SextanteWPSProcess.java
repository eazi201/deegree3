//$HeadURL: http://svn.wald.intevation.org/svn/deegree/base/trunk/resources/eclipse/files_template.xml $
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2010 by:
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
package org.deegree.services.wps.provider;

import java.util.LinkedList;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import org.deegree.services.jaxb.wps.ComplexFormatType;
import org.deegree.services.jaxb.wps.ComplexInputDefinition;
import org.deegree.services.jaxb.wps.ComplexOutputDefinition;
import org.deegree.services.jaxb.wps.LanguageStringType;
import org.deegree.services.jaxb.wps.ProcessDefinition;
import org.deegree.services.jaxb.wps.ProcessletInputDefinition;
import org.deegree.services.jaxb.wps.ProcessletOutputDefinition;
import org.deegree.services.jaxb.wps.ProcessDefinition.InputParameters;
import org.deegree.services.jaxb.wps.ProcessDefinition.OutputParameters;
import org.deegree.services.wps.ExceptionCustomizer;
import org.deegree.services.wps.Processlet;
import org.deegree.services.wps.WPSProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import es.unex.sextante.core.GeoAlgorithm;
import es.unex.sextante.core.OutputObjectsSet;
import es.unex.sextante.core.ParametersSet;
import es.unex.sextante.outputs.Output;
import es.unex.sextante.parameters.Parameter;

/**
 * Presents a SEXTANTE WPSProcess.
 * 
 * TODO more details
 * 
 * @author <a href="mailto:pabel@lat-lon.de">Jens Pabel</a>
 * @author last edited by: $Author: pabel $
 * 
 * @version $Revision: $, $Date: $
 */
public class SextanteWPSProcess implements WPSProcess {

    private static final Logger LOG = LoggerFactory.getLogger( SextanteWPSProcess.class );

    // input parameter types
    public static final String VECTOR_LAYER_INPUT = "Vector Layer";

    public static final String NUMERICAL_VALUE_INPUT = "Numerical Value";

    public static final String SELECTION_INPUT = "Numerical Value";

    public static final String FILEPATH_INPUT = "Filepath";

    public static final String BOOLEAN_INPUT = "Boolean";

    public static final String STRING_INPUT = "String";

    public static final String MULTIPLE_INPUT_INPUT = "Multiple Input";

    public static final String RASTER_LAYER_INPUT = "Raster Layer";

    public static final String TABLE_FIELD_INPUT = "Table Field";

    public static final String POINT_INPUT = "Point";

    public static final String BAND_INPUT = "Band";

    public static final String TABLE_INPUT = "Table";

    public static final String FIXED_TABLE_INPUT = "Fixed Table";

    // output parameter types
    public static final String VECTOR_LAYER_OUTPUT = "vector";

    public static final String RASTER_LAYER_OUTPUT = "raster";

    public static final String TABLE_OUTPUT = "table";

    public static final String TEXT_OUTPUT = "text";

    public static final String CHART_OUTPUT = "chart";

    // processlet
    private final SextanteProcesslet processlet;

    // process description
    private final ProcessDefinition description;

    // complex format types
    private LinkedList<ComplexFormatType> gmInputFormats = FormatHelper.getInputFormatsWithoutDefault();

    private LinkedList<ComplexFormatType> gmlOutputFormats = FormatHelper.getOutputFormatsWithoutDefault();

    SextanteWPSProcess( GeoAlgorithm alg ) {
        processlet = new SextanteProcesslet( alg );
        description = createDescription( alg );
    }

    @Override
    public Processlet getProcesslet() {
        return processlet;
    }

    @Override
    public ProcessDefinition getDescription() {
        return description;
    }

    @Override
    public ExceptionCustomizer getExceptionCustomizer() {
        return null;
    }

    /**
     * Creates a process definition for a SEXTANTE algorithm.
     * 
     * @param alg
     *            - SEXTANTE {@link GeoAlgorithm}
     * @return
     */
    private ProcessDefinition createDescription( GeoAlgorithm alg ) {

        // process definition
        ProcessDefinition processDefinition = new ProcessDefinition();
        processDefinition.setConfigVersion( "0.5.0" );
        processDefinition.setProcessVersion( "1.0.0" );
        processDefinition.setStatusSupported( false );

        // identifier
        org.deegree.services.jaxb.wps.CodeType identifier = new org.deegree.services.jaxb.wps.CodeType();
        identifier.setValue( alg.getCommandLineName() );
        processDefinition.setIdentifier( identifier );

        // title
        LanguageStringType title = new LanguageStringType();
        title.setValue( alg.getName() );
        processDefinition.setTitle( title );

        // abstract
        LanguageStringType abstr = new LanguageStringType();
        String help = "";
        try {
            help = alg.getCommandLineHelp();
        } catch ( StringIndexOutOfBoundsException e ) {
        }
        abstr.setValue( help );
        processDefinition.setAbstract( abstr );

        // define input parameters
        InputParameters input = new InputParameters();
        List<JAXBElement<? extends ProcessletInputDefinition>> listInput = input.getProcessInput();

        // traverses the input parameters
        for ( int i = 0; i < alg.getParameters().getNumberOfParameters(); i++ ) {

            // input parameter
            Parameter param = alg.getParameters().getParameter( i );

            // select the correct input parameter type
            String paramTypeName = param.getParameterTypeName();
            if ( param.getParameterTypeName().equals( VECTOR_LAYER_INPUT ) )
                listInput.add( createVectorLayerInputParameter( param ) );
            else if ( paramTypeName.equals( NUMERICAL_VALUE_INPUT ) )
                listInput.add( createNumericalValueInputParameter( param ) );
            else if ( paramTypeName.equals( SELECTION_INPUT ) )
                listInput.add( createSelectionInputParameter( param ) );
            else if ( paramTypeName.equals( FILEPATH_INPUT ) )
                listInput.add( createFilepathInputParameter( param ) );
            else if ( paramTypeName.equals( BOOLEAN_INPUT ) )
                listInput.add( createBooleanInputParameter( param ) );
            else if ( paramTypeName.equals( STRING_INPUT ) )
                listInput.add( createStringInputParameter( param ) );
            else if ( paramTypeName.equals( MULTIPLE_INPUT_INPUT ) )
                listInput.add( createMultipleInputInputParameter( param, alg.getParameters() ) );
            else if ( paramTypeName.equals( RASTER_LAYER_INPUT ) )
                listInput.add( createRasterLayerInputParameter( param ) );
            else if ( paramTypeName.equals( TABLE_FIELD_INPUT ) )
                listInput.add( createTableFieldInputParameter( param ) );
            else if ( paramTypeName.equals( POINT_INPUT ) )
                listInput.add( createPointInputParameter( param ) );
            else if ( paramTypeName.equals( BAND_INPUT ) )
                listInput.add( createBandInputParameter( param ) );
            else if ( paramTypeName.equals( TABLE_INPUT ) )
                listInput.add( createTableInputParameter( param ) );
            else if ( paramTypeName.equals( FIXED_TABLE_INPUT ) )
                listInput.add( createFixedTableInputParameter( param ) );
            else {
                LOG.error( "\"" + paramTypeName + "\" is a not supported input parameter type." );
                // TODO throw exception
            }

        }

        // set input parameters
        processDefinition.setInputParameters( input );

        // define output parameters
        OutputParameters output = new OutputParameters();
        List<JAXBElement<? extends ProcessletOutputDefinition>> listOutput = output.getProcessOutput();
        OutputObjectsSet outputObjects = alg.getOutputObjects();

        // traverses the output parameters
        for ( int i = 0; i < outputObjects.getOutputObjectsCount(); i++ ) {

            // output parameter
            Output param = outputObjects.getOutput( i );

            // select the correct output parameter type
            String paramTypeDesc = param.getTypeDescription();
            if ( paramTypeDesc.equals( VECTOR_LAYER_OUTPUT ) )
                listOutput.add( createVectorLayerOutputParameter( param ) );
            else if ( paramTypeDesc.equals( RASTER_LAYER_OUTPUT ) )
                listOutput.add( createRasterLayerOutputParameter( param ) );
            else if ( paramTypeDesc.equals( TABLE_OUTPUT ) )
                listOutput.add( createTableOutputParameter( param ) );
            else if ( paramTypeDesc.equals( TEXT_OUTPUT ) )
                listOutput.add( createTextOutputParameter( param ) );
            else if ( paramTypeDesc.equals( CHART_OUTPUT ) )
                listOutput.add( createChartOutputParameter( param ) );
            else {
                LOG.error( "\"" + paramTypeDesc + "\" is a not supported output parameter type." );
                // TODO throw exception
            }

        }

        // set output parameters
        processDefinition.setOutputParameters( output );

        return processDefinition;
    }

    /**
     * Returns a input parameter definition for a vector layer.
     * 
     * @param param
     *            - input parameter
     * @return
     */
    private JAXBElement<? extends ProcessletInputDefinition> createVectorLayerInputParameter( Parameter param ) {

        // ComplexInput
        QName complexInputName = new QName( "ComplexData" );
        ComplexInputDefinition complexInputValue = new ComplexInputDefinition();
        JAXBElement<ComplexInputDefinition> complexInput = new JAXBElement<ComplexInputDefinition>(
                                                                                                    complexInputName,
                                                                                                    ComplexInputDefinition.class,
                                                                                                    complexInputValue );
        // ComplexInput - Identifier
        org.deegree.services.jaxb.wps.CodeType complexInputIdentifier = new org.deegree.services.jaxb.wps.CodeType();
        complexInputIdentifier.setValue( param.getParameterName() );
        complexInputValue.setIdentifier( complexInputIdentifier );

        // ComplexInput - Title
        LanguageStringType complexInputTitle = new LanguageStringType();
        complexInputTitle.setValue( param.getParameterDescription() );
        complexInputValue.setTitle( complexInputTitle );

        // ComplexInput - Format
        complexInputValue.setDefaultFormat( FormatHelper.getDefaultInputFormat() );
        List<ComplexFormatType> inputOtherFormats = complexInputValue.getOtherFormats();
        inputOtherFormats.addAll( gmInputFormats );

        return complexInput;
    }

    /**
     * Returns a input parameter definition for a numerical value.
     * 
     * @param param
     *            - input parameter
     * @return
     */
    private JAXBElement<? extends ProcessletInputDefinition> createNumericalValueInputParameter( Parameter param ) {

        LOG.error( "\"" + param.getParameterTypeName()
                   + "\" a is not supported input parameter type (but is in implementation)" );

        // // LiteralInput
        // QName literalInputName = new QName( "LiteralData" );
        // LiteralInputDefinition literalInputValue = new LiteralInputDefinition();
        // JAXBElement<LiteralInputDefinition> literalInput = new JAXBElement<LiteralInputDefinition>(
        // literalInputName,
        // LiteralInputDefinition.class,
        // literalInputValue );
        // // LiteralInput - Identifier
        // org.deegree.services.jaxb.wps.CodeType literalInputIdentifier = new org.deegree.services.jaxb.wps.CodeType();
        // literalInputIdentifier.setValue( param.getParameterName() );
        // literalInputValue.setIdentifier( literalInputIdentifier );
        //
        // // LiteralInput - Title
        // LanguageStringType literalInputTitle = new LanguageStringType();
        // literalInputTitle.setValue( param.getParameterDescription() );
        // literalInputValue.setTitle( literalInputTitle );
        //
        // // LiteralInput - Format
        // DataType literalDataType = new DataType();
        // literalDataType.setValue( "double" );
        // literalInputValue.setDataType( literalDataType );

        return null;
    }

    /**
     * Returns a input parameter definition for a selection.
     * 
     * @param param
     *            input parameter
     * @return
     */
    private JAXBElement<? extends ProcessletInputDefinition> createSelectionInputParameter( Parameter param ) {
        LOG.error( "\"" + param.getParameterTypeName()
                   + "\" a is not supported input parameter type (but is in implementation)" );
        // TODO implement this input parameter type
        return null;
    }

    /**
     * Returns a input parameter definition for a filepath.
     * 
     * @param param
     *            input parameter
     * @return
     */
    private JAXBElement<? extends ProcessletInputDefinition> createFilepathInputParameter( Parameter param ) {
        LOG.error( "\"" + param.getParameterTypeName()
                   + "\" a is not supported input parameter type (but is in implementation)" );
        // TODO implement this input parameter type
        return null;
    }

    /**
     * Returns a input parameter definition for a boolean.
     * 
     * @param param
     *            input parameter
     * @return
     */
    private JAXBElement<? extends ProcessletInputDefinition> createBooleanInputParameter( Parameter param ) {
        LOG.error( "\"" + param.getParameterTypeName()
                   + "\" a is not supported input parameter type (but is in implementation)" );
        // TODO implement this input parameter type
        return null;
    }

    /**
     * Returns a input parameter definition for a multiple input.
     * 
     * @param param
     *            input parameter
     * @param paramSet
     *            set of parameters
     * @return
     */
    private JAXBElement<? extends ProcessletInputDefinition> createMultipleInputInputParameter( Parameter param,
                                                                                                ParametersSet paramSet ) {

        // type of vector layers
        if ( paramSet.getNumberOfVectorLayers( true ) - paramSet.getNumberOfVectorLayers( false ) > 0 ) {
            LOG.error( "\"" + param.getParameterTypeName()
                       + " - Vector Layer\" a is not supported input parameter type (but is in implementation)" );
            // TODO implement this input parameter type
        } else {

            // type of raster layers
            if ( paramSet.getNumberOfRasterLayers( true ) - paramSet.getNumberOfRasterLayers( false ) > 0 ) {
                LOG.error( "\"" + param.getParameterTypeName()
                           + " - Raster Layer\" a is not supported input parameter type (but is in implementation)" );
                // TODO implement this input parameter type

            } else {
                LOG.error( "\"" + param.getParameterTypeName()
                           + " - unknown type\" a is not supported input parameter type." );
                // TODO throw exception
            }
        }

        return null;
    }

    /**
     * Returns a input parameter definition for a string.
     * 
     * @param param
     *            input parameter
     * @return
     */
    private JAXBElement<? extends ProcessletInputDefinition> createStringInputParameter( Parameter param ) {
        LOG.error( "\"" + param.getParameterTypeName()
                   + "\" a is not supported input parameter type (but is in implementation)" );
        // TODO implement this input parameter type
        return null;
    }

    /**
     * Returns a input parameter definition for a raster layer.
     * 
     * @param param
     *            input parameter
     * @return
     */
    private JAXBElement<? extends ProcessletInputDefinition> createRasterLayerInputParameter( Parameter param ) {
        LOG.error( "\"" + param.getParameterTypeName()
                   + "\" a is not supported input parameter type (but is in implementation)" );
        // TODO implement this input parameter type
        return null;
    }

    /**
     * 
     * Returns a input parameter definition for a table field.
     * 
     * @param param
     *            input parameter
     * @return
     */
    private JAXBElement<? extends ProcessletInputDefinition> createTableFieldInputParameter( Parameter param ) {
        LOG.error( "\"" + param.getParameterTypeName()
                   + "\" a is not supported input parameter type (but is in implementation)" );
        // TODO implement this input parameter type
        return null;
    }

    /**
     * Returns a input parameter definition for a point.
     * 
     * @param param
     *            input parameter
     * @return
     */
    private JAXBElement<? extends ProcessletInputDefinition> createPointInputParameter( Parameter param ) {
        LOG.error( "\"" + param.getParameterTypeName()
                   + "\" a is not supported input parameter type (but is in implementation)" );
        // TODO implement this input parameter type
        return null;
    }

    /**
     * Returns a input parameter definition for a band.
     * 
     * @param param
     *            input parameter
     * @return
     */
    private JAXBElement<? extends ProcessletInputDefinition> createBandInputParameter( Parameter param ) {
        LOG.error( "\"" + param.getParameterTypeName()
                   + "\" a is not supported input parameter type (but is in implementation)" );
        // TODO implement this input parameter type
        return null;
    }

    /**
     * Returns a input parameter definition for a table.
     * 
     * @param param
     *            input parameter
     * @return
     */
    private JAXBElement<? extends ProcessletInputDefinition> createTableInputParameter( Parameter param ) {
        LOG.error( "\"" + param.getParameterTypeName()
                   + "\" a is not supported input parameter type (but is in implementation)" );
        // TODO implement this input parameter type
        return null;
    }

    /**
     * Returns a input parameter definition for a fixed table.
     * 
     * @param param
     *            input parameter
     * @return
     */
    private JAXBElement<? extends ProcessletInputDefinition> createFixedTableInputParameter( Parameter param ) {
        LOG.error( "\"" + param.getParameterTypeName()
                   + "\" a is not supported input parameter type (but is in implementation)" );
        // TODO implement this input parameter type
        return null;
    }

    /**
     * Returns a output parameter definition for a vector layer.
     * 
     * @param out
     *            output parameter
     * @return
     */
    private JAXBElement<? extends ProcessletOutputDefinition> createVectorLayerOutputParameter( Output out ) {

        // ComplexOutput
        QName complexOutputName = new QName( "ComplexOutput" );
        ComplexOutputDefinition complexOutputValue = new ComplexOutputDefinition();
        JAXBElement<ComplexOutputDefinition> complexOutput = new JAXBElement<ComplexOutputDefinition>(
                                                                                                       complexOutputName,
                                                                                                       ComplexOutputDefinition.class,
                                                                                                       complexOutputValue );
        // ComplexOutput - Identifier
        org.deegree.services.jaxb.wps.CodeType complexOutputIdentifier = new org.deegree.services.jaxb.wps.CodeType();
        complexOutputIdentifier.setValue( out.getName() );
        complexOutputValue.setIdentifier( complexOutputIdentifier );

        // ComplexOutput - Title
        LanguageStringType complexOutputTitle = new LanguageStringType();
        complexOutputTitle.setValue( out.getDescription() );
        complexOutputValue.setTitle( complexOutputTitle );

        // ComplexOutput - Format
        complexOutputValue.setDefaultFormat( FormatHelper.getDefaultOutputFormat() );
        List<ComplexFormatType> outputOtherFormats = complexOutputValue.getOtherFormats();
        outputOtherFormats.addAll( gmlOutputFormats );

        return complexOutput;
    }

    /**
     * Returns a output parameter definition for a raster layer.
     * 
     * @param out
     *            output parameter
     * @return
     */
    private JAXBElement<? extends ProcessletOutputDefinition> createRasterLayerOutputParameter( Output out ) {
        LOG.error( "\"" + out.getTypeDescription()
                   + "\" a is not supported output parameter type (but is in implementation)" );
        // TODO implement this output parameter type
        return null;
    }

    /**
     * Returns a output parameter definition for a table.
     * 
     * @param out
     *            output parameter
     * @return
     */
    private JAXBElement<? extends ProcessletOutputDefinition> createTableOutputParameter( Output out ) {
        LOG.error( "\"" + out.getTypeDescription()
                   + "\" a is not supported output parameter type (but is in implementation)" );
        // TODO implement this output parameter type
        return null;
    }

    /**
     * Returns a output parameter definition for a text.
     * 
     * @param out
     *            output parameter
     * @return
     */
    private JAXBElement<? extends ProcessletOutputDefinition> createTextOutputParameter( Output out ) {
        LOG.error( "\"" + out.getTypeDescription()
                   + "\" a is not supported output parameter type (but is in implementation)" );
        // TODO implement this output parameter type
        return null;
    }

    /**
     * Returns a output parameter definition for a chart.
     * 
     * @param out
     *            output parameter
     * @return
     */
    private JAXBElement<? extends ProcessletOutputDefinition> createChartOutputParameter( Output out ) {
        LOG.error( "\"" + out.getTypeDescription()
                   + "\" a is not supported output parameter type (but is in implementation)" );
        // TODO implement this output parameter type
        return null;
    }

    /**
     * Logs a SEXTANTE {@link GeoAlgorithm} with his input und output parameters.
     * 
     * @param alg
     *            - SEXTANTE {@link GeoAlgorithm}
     */
    public static void logAlgorithm( GeoAlgorithm alg ) {

        LOG.info( "ALGORITHM: " + alg.getCommandLineName() + " (" + alg.getName() + ")" );
        ParametersSet paramSet = alg.getParameters();
        for ( int i = 0; i < paramSet.getNumberOfParameters(); i++ ) {
            LOG.info( "InputParameter: " + paramSet.getParameter( i ).getParameterName() + " ("
                      + paramSet.getParameter( i ).getParameterTypeName() + ")" );
        }
        OutputObjectsSet outputSet = alg.getOutputObjects();
        for ( int i = 0; i < outputSet.getOutputDataObjectsCount(); i++ ) {
            LOG.info( "OutputParameter: " + outputSet.getOutput( i ).getName() + " ("
                      + outputSet.getOutput( i ).getTypeDescription() + ")" );
        }
    }

}
