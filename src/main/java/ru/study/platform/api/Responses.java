package ru.study.platform.api;

import java.net.URI;
import org.springframework.http.ResponseEntity;

final class Responses {

    private Responses() {}

    static <T> ResponseEntity<T> created(String path, long id, T body) {
        return ResponseEntity.created(URI.create("/api/" + path + "/" + id)).body(body);
    }
}
