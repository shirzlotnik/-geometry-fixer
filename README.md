# -geometry-fixer

For polygons that self intersects on existing coordinate, (the intersection point is part of the polygon coordinates)

Colons can be used to align columns.

| id                            | geometry                                                              | Fixed  |
| ----------------------------- |:---------------------------------------------------------------------:| ------:|
| polygon1                      | POLYGON ((1 -1, 0 -2, -1 -1, 0 0, 1 0, 0 2, -1 1, 0 0, 1 -1))         | POLYGON ((1 -1, 0 -2, -1 -1, -0.0007071067811865 -0.0007071067811865, -1 1, 0 2, 1 0, 0.0010, 1 -1))  |
| polygon2                      | POLYGON ((0 -1, -1 -1, -1 0, 0 0, 1 0, 1 1, 0 1, 0 0, 0 -1))           | POLYGON ((-1 0, -0.001 0, 0 1, 1 1, 1 0, 0.001 0, 0 -1, -1 -1, -1 0))   |
| polygon1_missing_closing_ring | POLYGON ((1 -1, 0 -2, -1 -1, 0 0, 1 0, 0 2, -1 1, 0 0, 1 -1))          | POLYGON ((-1 -1, -0.0007071067811865 -0.0007071067811865, -1 1, 0 2, 1 0, 0.001 0, 1 -1, 0 -2, -1 -1))    |
| bowtie_2      | POLYGON ((0 -2, -1.5 -0.5, 0 0, 1.5 0.5, 0 2, -1.6667 1.6667, 0 0, 1.6 -1.6, 0 -2))   | POLYGON ((-1.6667 1.6667, 0 2, 1.5 0.5, 0.0009486832980505 0.0003162277660168, 1.6 -1.6, 0 -2, -1.5 -0.5, -0.0009486832980505 -0.0003162277660168, -1.6667 1.6667))   |



## EXAMPLES


![image](https://github.com/user-attachments/assets/fdc8344a-2dad-4442-915a-3d5c80a3dae7)

<img width="1423" alt="image" src="https://github.com/user-attachments/assets/591d4f39-de8b-47a8-b89a-3cec2c01b21b" />

<img width="1414" alt="Screen Shot 2024-12-21 at 8 43 49 PM" src="https://github.com/user-attachments/assets/7bc06ac3-7fe2-47e0-882d-2d4799bedbc2" />

