package com.dawnduskterminal.geckolib3.geo.render;

import com.dawnduskterminal.geckolib3.geo.raw.tree.RawGeometryTree;
import com.dawnduskterminal.geckolib3.geo.render.built.GeoModel;


public interface IGeoBuilder {
    GeoModel constructGeoModel(RawGeometryTree geometryTree);
}
