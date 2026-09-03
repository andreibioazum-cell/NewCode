/*
 * nc_3d.h - маленькое 3D-ядро NewCode на чистом ISO C11.
 *
 * Модуль не зависит от Java/Kotlin, OpenGL или сборщика мусора. Он отвечает
 * за 3D-позу объектов, камеру и перспективную проекцию в горизонтальный
 * viewport. Рендерер платформы получает готовые экранные координаты.
 */
#ifndef NC_3D_H
#define NC_3D_H

#include <stdbool.h>

typedef struct { double x, y, z; } NcVec3;

typedef struct {
    NcVec3 position;
    /* Углы Эйлера в градусах: pitch вокруг X, yaw вокруг Y, roll вокруг Z. */
    NcVec3 rotation;
    NcVec3 scale;
} NcTransform3D;

typedef struct {
    NcVec3 position;
    NcVec3 rotation;
    double vertical_fov;
    double near_plane;
    double far_plane;
    int viewport_width;
    int viewport_height;
} NcCamera3D;

typedef struct {
    double x, y;       /* пиксели, начало координат в левом верхнем углу */
    double depth;      /* расстояние от камеры; меньше — ближе */
    bool visible;
} NcScreenPoint;

/* Горизонтальная камера 16:9: 1280x720, FOV 60°, near/far 0.1/1000. */
NcCamera3D nc_camera_landscape(void);
NcTransform3D nc_transform_identity(void);

NcVec3 nc_vec3_add(NcVec3 a, NcVec3 b);
NcVec3 nc_vec3_sub(NcVec3 a, NcVec3 b);
NcVec3 nc_vec3_scale(NcVec3 value, double factor);
double nc_vec3_dot(NcVec3 a, NcVec3 b);
NcVec3 nc_vec3_cross(NcVec3 a, NcVec3 b);
NcVec3 nc_vec3_normalize(NcVec3 value);

/* Применяет scale -> rotation X/Y/Z -> translation. */
NcVec3 nc_transform_point(const NcTransform3D *transform, NcVec3 point);

/* Проецирует мировую точку на экран. Точки вне depth/FOV помечаются invisible. */
NcScreenPoint nc_project_point(const NcCamera3D *camera, NcVec3 world);

/* Движение вперёд с учётом pitch/yaw объекта. */
void nc_move_forward(NcTransform3D *transform, double distance);

#endif
