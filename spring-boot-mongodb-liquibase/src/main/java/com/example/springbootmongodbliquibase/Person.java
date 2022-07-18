package com.example.springbootmongodbliquibase;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public record Person(@Id String id, String title){}
