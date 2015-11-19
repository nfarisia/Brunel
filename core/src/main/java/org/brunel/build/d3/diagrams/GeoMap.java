/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.brunel.build.d3.diagrams;

import org.brunel.build.d3.D3Util;
import org.brunel.build.d3.ElementDefinition;
import org.brunel.build.util.ElementDetails;
import org.brunel.build.util.PositionFields;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.maps.GeoAnalysis;
import org.brunel.maps.GeoMapping;
import org.brunel.maps.GeoProjection;
import org.brunel.maps.PointCollection;
import org.brunel.maps.Rect;
import org.brunel.model.VisSingle;

public class GeoMap extends D3Diagram {

    private final String idField;               // Field used for identifiers
    private final GeoMapping mapping;           // Mapping of identifiers to features

    public static GeoMapping makeMapping(VisSingle vis, Dataset data, PositionFields positions) {
        String idField = getIDField(vis);
        if (idField != null)
            return GeoAnalysis.instance().make(data.field(idField).categories(), vis.tDiagramParameters);
        PointCollection p = positions.getAllPoints();
        if (!p.isEmpty() || vis.tDiagramParameters != null)
            return GeoAnalysis.instance().makeForPoints(p, vis.tDiagramParameters);
        return null;
    }

    public GeoMap(VisSingle vis, Dataset data, GeoMapping mapping, ScriptWriter out) {
        super(vis, data, out);
        this.mapping = mapping;
        idField = getIDField(vis);
        if (mapping == null)
            throw new IllegalStateException("Maps need either a position field or key with the feature names; or another element to define positions");
    }

    private static String getIDField(VisSingle vis) {
        if (vis.fKeys.isEmpty()) {
            if (vis.positionFields().length == 0)
                return null;
            return vis.positionFields()[0];
        } else {
            return vis.fKeys.get(0).asField();
        }
    }

    public static String writeProjection(ScriptWriter out, Rect bounds) {
        out.comment("Define the projection");

        // Calculate a suitable projection
        GeoProjection projection = new GeoProjection("geom.inner_width", "geom.inner_height", "winkel3");
        String[] projectionDescription = projection.makeProjection(bounds).split("\n");
        out.indentMore();
        out.add("var ");
        out.indentMore();

        // Define Winkel Tripel function if needed
        if (projectionDescription[0].contains("winkel3")) {
            String[] strings = GeoProjection.WinkelD3Function;
            out.add("winkel3 =", strings[0]);
            out.indentMore();
            for (int i = 1; i < strings.length; i++)
                out.onNewLine().add(strings[i]);
            out.add(",").onNewLine();
            out.indentLess();

        }

        // Define the projection
        out.add("projection =", projectionDescription[0].trim());
        out.indentMore();
        for (int i = 1; i < projectionDescription.length; i++)
            out.onNewLine().add(projectionDescription[i].trim());
        out.add(",").onNewLine();
        out.indentLess();


        // Always add pan/zoom for now -- should check to see if turned off
        out.add("zoom = d3.behavior.zoom()")
                .addChained("scale(projection.scale()).translate(projection.translate())")
                .addChained("on('zoom', function() {").onNewLine();
        out.indentMore();
        out.add("projection.translate(zoom.translate()).scale(zoom.scale())").endStatement();
        out.add("rebuildSystem(0)").endStatement();
        out.indentLess().add("})");
        out.endStatement();
        out.indentLess();
        out.add("vis.call(zoom)").endStatement();
        // Define a 'safe' version of projection which sends failed projection points off the screen
        // This occurs for the albersUSA projection ...
        out.add("function projectTransform(p) {").indentMore()
                .onNewLine().add("if (p.c) p = p.c").endStatement()
                .onNewLine().add("var q = projection(p)").endStatement()
                .onNewLine().add("if (!q) q = [-9e9,9e9]").endStatement()
                .onNewLine().add("return 'translate(' + q[0] + ',' + q[1] + ')'")
                .indentLess().onNewLine().add("}");

        return projection.projectionName;
    }

    public void writeDiagramEnter() {
        super.writeDiagramEnter();
    }

    public ElementDetails writeDataConstruction() {
        out.add("var path = d3.geo.path().projection(projection)").endStatement();
        out.indentLess();

        out.comment("Read in the feature data and call build again when done");
        writeFeatureHookup(mapping, idField);

        // The labeling will be defined later and then used when we do the actual layout call to define the D3 data
        return ElementDetails.makeForDiagram("data._rows", "path", "polygon", "geo", false);
    }

    private void writeFeatureHookup(GeoMapping mapping, String idField) {
        if (mapping.fileCount() == 0) throw new IllegalStateException("No suitable map found");

        out.add("var features = ");
        GeoAnalysis.writeMapping(out, mapping);
        out.endStatement();

        String idName = idField == null ? "null" : "data." + D3Util.canonicalFieldName(idField);
        out.add("if (BrunelD3.addFeatures(data, features,", idName, ", this, transitionMillis)) return").endStatement();
    }

    public void writeDefinition(ElementDetails details, ElementDefinition elementDef) {
        // Set the given location using the transform
        out.addChained("attr('d', path )").endStatement();
        addAestheticsAndTooltips(details, true);
    }

    public void writePreDefinition(ElementDetails details, ElementDefinition elementDef) {
        out.add("selection.classed('nondata', function(d) {return !d || d.row == null})").endStatement();
    }

}
