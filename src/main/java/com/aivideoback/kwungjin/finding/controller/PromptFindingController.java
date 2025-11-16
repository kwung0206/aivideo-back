// src/main/java/com/aivideoback/kwungjin/finding/PromptFindingController.java
package com.aivideoback.kwungjin.finding.controller;

import com.aivideoback.kwungjin.finding.dto.PromptFindingRequest;
import com.aivideoback.kwungjin.finding.dto.PromptFindingResponse;
import com.aivideoback.kwungjin.finding.service.PromptFindingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finding")
@RequiredArgsConstructor
public class PromptFindingController {

    private final PromptFindingService promptFindingService;

    @PostMapping("/search")
    public PromptFindingResponse search(@Valid @RequestBody PromptFindingRequest request) {
        return promptFindingService.search(request);
    }
}
