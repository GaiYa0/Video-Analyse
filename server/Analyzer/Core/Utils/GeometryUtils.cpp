#include "GeometryUtils.h"

#include <algorithm>
#include <cmath>

namespace SVAAnalyzer
{
    namespace
    {
        static constexpr double kEpsilon = 1e-8;

        static double cross(const GeometryPoint &origin, const GeometryPoint &lhs, const GeometryPoint &rhs)
        {
            return (lhs.x - origin.x) * (rhs.y - origin.y) - (lhs.y - origin.y) * (rhs.x - origin.x);
        }

        static bool isPointOnSegment(const GeometryPoint &point, const GeometryLineSegment &segment)
        {
            const double area = std::fabs(cross(segment.start, segment.end, point));
            if (area > kEpsilon)
            {
                return false;
            }

            const double minX = std::min(segment.start.x, segment.end.x) - kEpsilon;
            const double maxX = std::max(segment.start.x, segment.end.x) + kEpsilon;
            const double minY = std::min(segment.start.y, segment.end.y) - kEpsilon;
            const double maxY = std::max(segment.start.y, segment.end.y) + kEpsilon;
            return point.x >= minX && point.x <= maxX && point.y >= minY && point.y <= maxY;
        }

        static int orientation(const GeometryPoint &lhs, const GeometryPoint &rhs, const GeometryPoint &point)
        {
            const double value = cross(lhs, rhs, point);
            if (std::fabs(value) <= kEpsilon)
            {
                return 0;
            }
            return value > 0.0 ? 1 : -1;
        }
    }

    GeometryPoint calcBoxCenter(int x1, int y1, int x2, int y2)
    {
        GeometryPoint point;
        point.x = (static_cast<double>(x1) + static_cast<double>(x2)) * 0.5;
        point.y = (static_cast<double>(y1) + static_cast<double>(y2)) * 0.5;
        return point;
    }

    std::vector<GeometryPoint> buildPolygonPoints(const std::vector<double> &coords)
    {
        std::vector<GeometryPoint> polygon;
        if (coords.size() < 6 || (coords.size() % 2) != 0)
        {
            return polygon;
        }

        polygon.reserve(coords.size() / 2);
        for (size_t i = 0; i + 1 < coords.size(); i += 2)
        {
            polygon.push_back(GeometryPoint{coords[i], coords[i + 1]});
        }
        return polygon;
    }

    bool isPointInPolygon(const GeometryPoint &point, const std::vector<GeometryPoint> &polygon)
    {
        if (polygon.size() < 3)
        {
            return false;
        }

        bool inside = false;
        for (size_t i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++)
        {
            GeometryLineSegment edge{polygon[j], polygon[i]};
            if (isPointOnSegment(point, edge))
            {
                return true;
            }

            const bool intersectsY = ((polygon[i].y > point.y) != (polygon[j].y > point.y));
            if (!intersectsY)
            {
                continue;
            }

            const double xCross = (polygon[j].x - polygon[i].x) * (point.y - polygon[i].y) /
                                      (polygon[j].y - polygon[i].y + ((polygon[j].y - polygon[i].y) >= 0.0 ? kEpsilon : -kEpsilon)) +
                                  polygon[i].x;
            if (xCross >= point.x - kEpsilon)
            {
                inside = !inside;
            }
        }
        return inside;
    }

    bool isBoxCenterInPolygon(int x1, int y1, int x2, int y2, const std::vector<double> &polygonCoords)
    {
        const std::vector<GeometryPoint> polygon = buildPolygonPoints(polygonCoords);
        if (polygon.empty())
        {
            return true;
        }
        return isPointInPolygon(calcBoxCenter(x1, y1, x2, y2), polygon);
    }

    double distancePointToSegment(const GeometryPoint &point, const GeometryLineSegment &segment)
    {
        const double dx = segment.end.x - segment.start.x;
        const double dy = segment.end.y - segment.start.y;
        const double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= kEpsilon)
        {
            const double px = point.x - segment.start.x;
            const double py = point.y - segment.start.y;
            return std::sqrt(px * px + py * py);
        }

        const double projection = ((point.x - segment.start.x) * dx + (point.y - segment.start.y) * dy) / lengthSquared;
        const double clampedProjection = std::max(0.0, std::min(1.0, projection));
        const double closestX = segment.start.x + clampedProjection * dx;
        const double closestY = segment.start.y + clampedProjection * dy;
        const double offsetX = point.x - closestX;
        const double offsetY = point.y - closestY;
        return std::sqrt(offsetX * offsetX + offsetY * offsetY);
    }

    bool doSegmentsIntersect(const GeometryLineSegment &lhs, const GeometryLineSegment &rhs)
    {
        const int o1 = orientation(lhs.start, lhs.end, rhs.start);
        const int o2 = orientation(lhs.start, lhs.end, rhs.end);
        const int o3 = orientation(rhs.start, rhs.end, lhs.start);
        const int o4 = orientation(rhs.start, rhs.end, lhs.end);

        if (o1 != o2 && o3 != o4)
        {
            return true;
        }

        if (o1 == 0 && isPointOnSegment(rhs.start, lhs))
        {
            return true;
        }
        if (o2 == 0 && isPointOnSegment(rhs.end, lhs))
        {
            return true;
        }
        if (o3 == 0 && isPointOnSegment(lhs.start, rhs))
        {
            return true;
        }
        if (o4 == 0 && isPointOnSegment(lhs.end, rhs))
        {
            return true;
        }
        return false;
    }

    bool didTrailCrossLine(const std::vector<TrackTrailPoint> &trail, const GeometryLineSegment &line)
    {
        if (trail.size() < 2)
        {
            return false;
        }

        for (size_t i = 1; i < trail.size(); ++i)
        {
            GeometryLineSegment motion;
            motion.start.x = static_cast<double>(trail[i - 1].x);
            motion.start.y = static_cast<double>(trail[i - 1].y);
            motion.end.x = static_cast<double>(trail[i].x);
            motion.end.y = static_cast<double>(trail[i].y);
            if (doSegmentsIntersect(motion, line))
            {
                return true;
            }
        }
        return false;
    }
}