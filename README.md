# -geometry-fixer

For polygons that self intersects on existing coordinate, (the intersection point is part of the polygon coordinates)

Colons can be used to align columns.

| id                            | geometry                                                              | Fixed  |
| ----------------------------- |:---------------------------------------------------------------------:| ------:|
| polygon1                      | POLYGON ((1 -1, 0 -2, -1 -1, 0 0, 1 0, 0 2, -1 1, 0 0, 1 -1))         | POLYGON ((1 -1, 0 -2, -1 -1, -0.0007071067811865 -0.0007071067811865, -1 1, 0 2, 1 0, 0.0010, 1 -1))  |
| polygon2                      | POLYGON ((0 -1, -1 -1, -1 0, 0 0, 1 0, 1 1, 0 1, 0 0, 0 -1))           | POLYGON ((-1 0, -0.001 0, 0 1, 1 1, 1 0, 0.001 0, 0 -1, -1 -1, -1 0))   |
| polygon1_missing_closing_ring | POLYGON ((1 -1, 0 -2, -1 -1, 0 0, 1 0, 0 2, -1 1, 0 0, 1 -1))          | POLYGON ((-1 -1, -0.0007071067811865 -0.0007071067811865, -1 1, 0 2, 1 0, 0.001 0, 1 -1, 0 -2, -1 -1))    |
| bowtie_2      | POLYGON ((0 -2, -1.5 -0.5, 0 0, 1.5 0.5, 0 2, -1.6667 1.6667, 0 0, 1.6 -1.6, 0 -2))   | POLYGON ((-1.6667 1.6667, 0 2, 1.5 0.5, 0.0009486832980505 0.0003162277660168, 1.6 -1.6, 0 -2, -1.5 -0.5, -0.0009486832980505 -0.0003162277660168, -1.6667 1.6667))   |
| wierd_bowtie_2      | POLYGON ((0 -2, -1.5 -0.5, 0 0, 1.6 0.4, 0 2, -1.6667 1.6667, 0 0, 1.6 -1.6, 0 -2))   | POLYGON ((0 0, -1.6667 1.6667, 0 2, 1.6 0.4, 0.0009701425001453 0.0002425356250363, 1.6 -1.6, 0 -2, -1.5 -0.5, 0 0))   |
| extra_polygon      | POLYGON ((0 0, 1 8, 5 4, 4 1, 0 0, -3 0, -6 -3, -5 -5, -2 -4, 0 0, -4 4, -1 5, 0 0))   | POLYGON ((0 0, -2 -4, -5 -5, -6 -3, -3 0, -0.001 0, -4 4, -1 5, -0.0001961161351382 0.0009805806756909, 1 8, 5 4, 4 1, 0 0))   |
| multi_intersection_points_1      | POLYGON ((-6 0, -4 4, 0 0, 5 3, 6 1, 10 7, 11 2, 6 1, 0 0, -6 0))   | POLYGON ((-6 0, -4 4, -0.0007071067811865 0.0007071067811865, 5 3, 5.9995527864045 1.0008944271909999, 10 7, 11 2, 6.000980580675691 1.0001961161351383, 0.0009863939238321 0.0001643989873054, -6 0))   |
| multi_intersection_points_3      | POLYGON ((-6 0, -4 4, 0 0, 1 2, 5 3, 6 1, 10 7, 13 4, 11 2, 6 1, 0 0, -6 0))   | POLYGON ((-6 0, -4 4, -0.0007071067811865 0.0007071067811865, 1 2, 5 3, 5.9995527864045 1.0008944271909999, 10 7, 13 4, 11 2, 6.000980580675691 1.0001961161351383, 0.0009863939238321 0.0001643989873054, -6 0))   |
| multi_intersection_points_4      | POLYGON ((-6 0, -4 4, 0 0, 1 2, 5 3, 6 1, 10 7, 13 4, 11 2, 6 1, 4 -6, 0 0, -6 0))   | POLYGON ((0 0, -6 0, -4 4, -0.0007071067811865 0.0007071067811865, 1 2, 5 3, 5.9995527864045 1.0008944271909999, 10 7, 13 4, 11 2, 6.000980580675691 1.0001961161351383, 4 -6, 0 0))   |



## EXAMPLES


![image](https://github.com/user-attachments/assets/fdc8344a-2dad-4442-915a-3d5c80a3dae7)

<img width="1423" alt="image" src="https://github.com/user-attachments/assets/591d4f39-de8b-47a8-b89a-3cec2c01b21b" />

<img width="1414" alt="Screen Shot 2024-12-21 at 8 43 49 PM" src="https://github.com/user-attachments/assets/7bc06ac3-7fe2-47e0-882d-2d4799bedbc2" />

