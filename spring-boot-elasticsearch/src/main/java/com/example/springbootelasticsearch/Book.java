package com.example.springbootelasticsearch;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

@Document(indexName = "books")
public class Book {

	@Id
	private String id;

	private String title;

	public Book() {
	}

	public Book(String id, String title) {
		this.id = id;
		this.title = title;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

}
