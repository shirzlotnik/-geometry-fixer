# -geometry-fixer

For polygons that self intersects on existing coordinate, (the intersection point is part of the polygon coordinates)

Colons can be used to align columns. <br />

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



## EXAMPLES OF REARRANGING COORDINATES <br />

### multi_intersection_points_x1   <br />
### BEFORE  <br />
<img width="691" alt="Screen Shot 2025-01-11 at 4 52 45 PM" src="https://github.com/user-attachments/assets/742afa4a-aa27-4346-8139-5ac50dbac299" />  <br />
The polygon self intersects at (0,0) and (6,1)  <br />
<img width="889" alt="Screen Shot 2025-01-11 at 4 53 12 PM" src="https://github.com/user-attachments/assets/ac649be2-5d82-48b7-a77f-5afaaf264712" />  <br />
<br />
### AFTER  <br />
<img width="656" alt="Screen Shot 2025-01-11 at 4 55 40 PM" src="https://github.com/user-attachments/assets/c9b1595c-6a50-4552-9f83-b5bd47ddbb89" />  <br />
From distance it looks like there is no change, but very closly the line does not self intersects in (0,0) or (6,1) - there is a tiny buffer (epsilon should be ~ 0.000000000001)  <br />
<img width="660" alt="Screen Shot 2025-01-11 at 4 55 53 PM" src="https://github.com/user-attachments/assets/d3ccae27-7e66-4f85-9541-b54b810d8d05" />  <br />
<br />

### polygon2  <br />
### BEFORE  <br />
<img width="524" alt="Screen Shot 2025-01-11 at 4 59 23 PM" src="https://github.com/user-attachments/assets/1efb9949-18f6-4945-a339-4f8ed13cc309" />  <br />
The polygon self intersects at (0,0)  <br />
<img width="578" alt="Screen Shot 2025-01-11 at 4 59 34 PM" src="https://github.com/user-attachments/assets/def5726b-c3c7-4b15-a548-c0edcb2503c9" />  <br />
  <br />

### AFTER  <br />
<img width="595" alt="Screen Shot 2025-01-11 at 5 01 02 PM" src="https://github.com/user-attachments/assets/02e35277-4b82-4a58-abb7-7d209ab74d7a" />  <br />
From distance it looks like there is no change, but very closly the line does not self intersects in (0,0) - there is a tiny buffer (epsilon should be ~ 0.000000000001)  <br />
<img width="741" alt="Screen Shot 2025-01-11 at 5 01 22 PM" src="https://github.com/user-attachments/assets/445e4dbc-e076-43ab-aa71-342be8d1f62a" />  <br />
<br />
<br />
## EXAMPLES OF REMOVING PARALLEL LINES AND DUPLICATES COORDINATES  
### BEFORE  
<img width="811" alt="Screen Shot 2025-01-11 at 4 43 36 PM" src="https://github.com/user-attachments/assets/e020e8ed-36f1-46b3-8f59-9f98eb136c11" />  
  
### AFTER  
<img width="727" alt="Screen Shot 2025-01-11 at 4 45 58 PM" src="https://github.com/user-attachments/assets/9d645d81-a193-4ae1-aa2a-50d2dbec8b8a" />  






![image](https://github.com/user-attachments/assets/fdc8344a-2dad-4442-915a-3d5c80a3dae7)

<img width="1423" alt="image" src="https://github.com/user-attachments/assets/591d4f39-de8b-47a8-b89a-3cec2c01b21b" />

<img width="1414" alt="Screen Shot 2024-12-21 at 8 43 49 PM" src="https://github.com/user-attachments/assets/7bc06ac3-7fe2-47e0-882d-2d4799bedbc2" />


