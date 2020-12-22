CREATE TABLE IF NOT EXISTS patients (
       id SERIAL PRIMARY KEY,
       first_name VARCHAR(50) NOT NULL,
       middle_name VARCHAR(50) NOT NULL,
       last_name VARCHAR(50) NOT NULL,
       gender VARCHAR(6) NOT NULL,
       birth_date DATE NOT NULL,
       address VARCHAR(200) NOT NULL,
       oms_number CHAR(16) NOT NULL
);
