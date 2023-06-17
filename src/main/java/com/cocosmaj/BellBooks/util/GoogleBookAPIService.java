package com.cocosmaj.BellBooks.util;

import com.cocosmaj.BellBooks.repository.shipment.BookRepository;
import com.cocosmaj.BellBooks.repository.shipment.PackageContentRepository;
import com.cocosmaj.BellBooks.model.shipment.Book;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class GoogleBookAPIService {

    private PackageContentRepository packageContentRepository;

    private BookRepository bookRepository;

    private HttpClient httpClient;


    public GoogleBookAPIService(PackageContentRepository packageContentRepository, BookRepository bookRepository){
        this.packageContentRepository = packageContentRepository;
        this.bookRepository = bookRepository;
        this.httpClient = HttpClient.newHttpClient();
    }

    public Book queryGoogle(String isbn) {
        Book book = new Book();
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(String.format("https://www.googleapis.com/books/v1/volumes?q=isbn:%s", isbn)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject googleBook = new JSONObject(response.body());
            JSONArray items = googleBook.getJSONArray("items");
            for (int index = 0; index < items.length(); index++) {
                JSONObject item = items.getJSONObject(index);

                if(!item.has("volumeInfo")) continue;
                JSONObject volumeInfo = item.getJSONObject("volumeInfo");

                if(!volumeInfo.has("title")) continue;
                book.setTitle(volumeInfo.getString("title"));

                if(volumeInfo.has("authors")) {
                    List<String> authors = volumeInfo.getJSONArray("authors").toList().stream()
                            .map(authorName -> (String) authorName)
                            .collect(Collectors.toList());
                    book.setAuthors(String.join("; ", authors));
                }

                if(!volumeInfo.has("industryIdentifiers")) continue;
                JSONArray industryIdentifiers = volumeInfo.getJSONArray("industryIdentifiers");
                for (int isbnIndex = 0; isbnIndex < industryIdentifiers.length(); isbnIndex++) {
                    if (book.getISBN10() != null && book.getISBN13() != null) break;

                    JSONObject identifier = industryIdentifiers.getJSONObject(isbnIndex);

                    if (book.getISBN10() == null && identifier.has("identifier") && identifier.has("type") && identifier.getString("type").equals("ISBN_10")) {
                        String googleIsbn = identifier.getString("identifier").replaceAll("-", "");
                        book.setISBN10(googleIsbn);
                    }
                    if (book.getISBN13() == null && identifier.has("identifier") && identifier.has("type") && identifier.getString("type").equals("ISBN_13")) {
                        String googleIsbn = identifier.getString("identifier").replaceAll("-", "");
                        book.setISBN13(googleIsbn);
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        if(book.getTitle() != null && book.getISBN10() == null && book.getISBN13() == null) {
            if(isbn.length() == 10) {
                book.setISBN10(isbn);
            } else {
                book.setISBN13(isbn);
            }
        }

        if(book.getTitle() == null) throw new RuntimeException("Book from Google does not have a title");
        return (Book) packageContentRepository.save(book);
    }

    public List<Book> queryGoogle(String title, String author) {
        LinkedList books = new LinkedList();
        title = title.replaceAll(" ", "+");
        author = author.replaceAll(" ", "+");
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(String.format("https://www.googleapis.com/books/v1/volumes?q=intitle:%s+inauthor:%s&maxResults=10", title, author)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject googleBooks = new JSONObject(response.body());
            JSONArray items = googleBooks.getJSONArray("items");
            for (int index = 0; index < items.length(); index++) {
                Book book = new Book();
                JSONObject item = items.getJSONObject(index);

                if(!item.has("volumeInfo")) continue;
                JSONObject volumeInfo = item.getJSONObject("volumeInfo");

                if(!volumeInfo.has("title")) continue;
                book.setTitle(volumeInfo.getString("title"));

                if(volumeInfo.has("authors")) {
                    List<String> authors = volumeInfo.getJSONArray("authors").toList().stream()
                            .map(authorName -> (String) authorName)
                            .collect(Collectors.toList());
                    book.setAuthors(String.join("; ", authors));
                }

                if(!volumeInfo.has("industryIdentifiers")) continue;
                JSONArray industryIdentifiers = volumeInfo.getJSONArray("industryIdentifiers");
                for (int isbnIndex = 0; isbnIndex < industryIdentifiers.length(); isbnIndex++) {
                    if (book.getISBN10() != null && book.getISBN13() != null) break;

                    JSONObject identifier = industryIdentifiers.getJSONObject(isbnIndex);

                    if (book.getISBN10() == null && identifier.has("identifier") && identifier.has("type") && identifier.getString("type").equals("ISBN_10")) {
                        String googleIsbn = identifier.getString("identifier").replaceAll("-", "");
                        book.setISBN10(googleIsbn);
                    }
                    if (book.getISBN13() == null && identifier.has("identifier") && identifier.has("type") && identifier.getString("type").equals("ISBN_13")) {
                        String googleIsbn = identifier.getString("identifier").replaceAll("-", "");
                        book.setISBN13(googleIsbn);
                    }
                }
                books.add(book);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        if(books.isEmpty()) throw new RuntimeException("Did not match any book found with Google.");
        return books;
    }


}
