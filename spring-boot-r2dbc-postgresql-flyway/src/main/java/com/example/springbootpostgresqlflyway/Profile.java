package com.example.springbootpostgresqlflyway;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

public class Profile {

    @Id
    @Column("id")
    Long id;
    
    @Column("name")
    String name;
}
