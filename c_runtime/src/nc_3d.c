#include "nc_3d.h"

#include <math.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

static double radians(double degrees) { return degrees * M_PI / 180.0; }

NcCamera3D nc_camera_landscape(void) {
    NcCamera3D camera = {
        {0.0, 0.0, 0.0}, {0.0, 0.0, 0.0},
        60.0, 0.1, 1000.0, 1280, 720
    };
    return camera;
}

NcTransform3D nc_transform_identity(void) {
    NcTransform3D transform = {{0.0, 0.0, 0.0}, {0.0, 0.0, 0.0}, {1.0, 1.0, 1.0}};
    return transform;
}

NcVec3 nc_vec3_add(NcVec3 a, NcVec3 b) { return (NcVec3){a.x+b.x, a.y+b.y, a.z+b.z}; }
NcVec3 nc_vec3_sub(NcVec3 a, NcVec3 b) { return (NcVec3){a.x-b.x, a.y-b.y, a.z-b.z}; }
NcVec3 nc_vec3_scale(NcVec3 v, double f) { return (NcVec3){v.x*f, v.y*f, v.z*f}; }
double nc_vec3_dot(NcVec3 a, NcVec3 b) { return a.x*b.x + a.y*b.y + a.z*b.z; }
NcVec3 nc_vec3_cross(NcVec3 a, NcVec3 b) {
    return (NcVec3){a.y*b.z-a.z*b.y, a.z*b.x-a.x*b.z, a.x*b.y-a.y*b.x};
}
NcVec3 nc_vec3_normalize(NcVec3 v) {
    double length = sqrt(nc_vec3_dot(v, v));
    return length > 1e-12 ? nc_vec3_scale(v, 1.0/length) : (NcVec3){0.0, 0.0, 0.0};
}

static NcVec3 rotate_x(NcVec3 v, double angle) {
    double c=cos(angle), s=sin(angle);
    return (NcVec3){v.x, v.y*c-v.z*s, v.y*s+v.z*c};
}
static NcVec3 rotate_y(NcVec3 v, double angle) {
    double c=cos(angle), s=sin(angle);
    return (NcVec3){v.x*c+v.z*s, v.y, -v.x*s+v.z*c};
}
static NcVec3 rotate_z(NcVec3 v, double angle) {
    double c=cos(angle), s=sin(angle);
    return (NcVec3){v.x*c-v.y*s, v.x*s+v.y*c, v.z};
}

NcVec3 nc_transform_point(const NcTransform3D *t, NcVec3 point) {
    if (!t) return point;
    NcVec3 v = {point.x*t->scale.x, point.y*t->scale.y, point.z*t->scale.z};
    v = rotate_x(v, radians(t->rotation.x));
    v = rotate_y(v, radians(t->rotation.y));
    v = rotate_z(v, radians(t->rotation.z));
    return nc_vec3_add(v, t->position);
}

NcScreenPoint nc_project_point(const NcCamera3D *camera, NcVec3 world) {
    NcScreenPoint result = {0.0, 0.0, 0.0, false};
    if (!camera || camera->viewport_width <= 0 || camera->viewport_height <= 0) return result;

    /* Обратное преобразование камеры. В NewCode положительная Z смотрит вперёд. */
    NcVec3 local = nc_vec3_sub(world, camera->position);
    local = rotate_z(local, -radians(camera->rotation.z));
    local = rotate_y(local, -radians(camera->rotation.y));
    local = rotate_x(local, -radians(camera->rotation.x));
    result.depth = local.z;
    if (local.z < camera->near_plane || local.z > camera->far_plane) return result;

    double focal = 1.0 / tan(radians(camera->vertical_fov) * 0.5);
    double aspect = (double)camera->viewport_width / (double)camera->viewport_height;
    double ndc_x = local.x * focal / (local.z * aspect);
    double ndc_y = local.y * focal / local.z;
    result.x = (ndc_x + 1.0) * 0.5 * camera->viewport_width;
    result.y = (1.0 - ndc_y) * 0.5 * camera->viewport_height;
    result.visible = ndc_x >= -1.0 && ndc_x <= 1.0 && ndc_y >= -1.0 && ndc_y <= 1.0;
    return result;
}

void nc_move_forward(NcTransform3D *transform, double distance) {
    if (!transform) return;
    double pitch = radians(transform->rotation.x);
    double yaw = radians(transform->rotation.y);
    NcVec3 forward = {sin(yaw)*cos(pitch), -sin(pitch), cos(yaw)*cos(pitch)};
    transform->position = nc_vec3_add(transform->position, nc_vec3_scale(forward, distance));
}
