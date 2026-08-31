#ifndef ANALYZER_GEOMETRYUTILS_H
#define ANALYZER_GEOMETRYUTILS_H

#include "../TrackMetadata.h"

#include <vector>

namespace SVAAnalyzer
{
    struct GeometryPoint
    {
        double x = 0.0;
        double y = 0.0;
    };

    struct GeometryLineSegment
    {
        GeometryPoint start;
        GeometryPoint end;
    };

    GeometryPoint calcBoxCenter(int x1, int y1, int x2, int y2);
    std::vector<GeometryPoint> buildPolygonPoints(const std::vector<double> &coords);
    bool isPointInPolygon(const GeometryPoint &point, const std::vector<GeometryPoint> &polygon);
    bool isBoxCenterInPolygon(int x1, int y1, int x2, int y2, const std::vector<double> &polygonCoords);
    double distancePointToSegment(const GeometryPoint &point, const GeometryLineSegment &segment);
    bool doSegmentsIntersect(const GeometryLineSegment &lhs, const GeometryLineSegment &rhs);
    bool didTrailCrossLine(const std::vector<TrackTrailPoint> &trail, const GeometryLineSegment &line);
}

#endif // ANALYZER_GEOMETRYUTILS_H