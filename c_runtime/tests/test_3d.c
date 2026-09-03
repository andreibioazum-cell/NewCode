#include "nc_3d.h"
#include "cat_compiler.h"
#include "cat_loader.h"
#include "cat_mem.h"

#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <string.h>

static int close_to(double a, double b) { return fabs(a-b) < 1e-7; }

int main(void) {
    NcCamera3D camera = nc_camera_landscape();
    assert(camera.viewport_width == 1280 && camera.viewport_height == 720);

    NcScreenPoint center = nc_project_point(&camera, (NcVec3){0, 0, 10});
    assert(center.visible);
    assert(close_to(center.x, 640.0));
    assert(close_to(center.y, 360.0));
    assert(close_to(center.depth, 10.0));

    assert(!nc_project_point(&camera, (NcVec3){0, 0, -1}).visible);
    assert(!nc_project_point(&camera, (NcVec3){100, 0, 1}).visible);

    NcTransform3D object = nc_transform_identity();
    object.position = (NcVec3){1, 2, 3};
    object.rotation.y = 90;
    NcVec3 transformed = nc_transform_point(&object, (NcVec3){0, 0, 2});
    assert(close_to(transformed.x, 3));
    assert(close_to(transformed.y, 2));
    assert(close_to(transformed.z, 3));

    object = nc_transform_identity();
    object.rotation.y = 90;
    nc_move_forward(&object, 5);
    assert(close_to(object.position.x, 5));
    assert(close_to(object.position.z, 0));

    NcVec3 normal = nc_vec3_normalize((NcVec3){0, 3, 4});
    assert(close_to(normal.y, 0.6));
    assert(close_to(normal.z, 0.8));

    /* Визуальные 3D-блоки должны попадать именно в нативный C-код. */
    const char *xml = "<program><objectList><object><name>Cube</name><scriptList>"
        "<script type='StartScript'><brickList>"
        "<brick type='SetZBrick'><formulaList><formula category='z'><type>NUMBER</type><value>10</value></formula></formulaList></brick>"
        "<brick type='SetRotationYBrick'><formulaList><formula category='degrees'><type>NUMBER</type><value>90</value></formula></formulaList></brick>"
        "<brick type='MoveForward3DBrick'><formulaList><formula category='steps'><type>NUMBER</type><value>5</value></formula></formulaList></brick>"
        "</brickList></script></scriptList></object></objectList></program>";
    CatProject *project = cat_load_project_xml_str(xml);
    assert(project && cat_loader_last_unknown_count() == 0);
    char *generated = cat_compile_to_c(project);
    assert(strstr(generated, "SP->z = 10") != NULL);
    assert(strstr(generated, "SP->rotation_y = 90") != NULL);
    assert(strstr(generated, "SP->z +=") != NULL);
    cat_free(generated);
    cat_project_free(project);

    puts("3D engine tests passed.");
    return 0;
}
