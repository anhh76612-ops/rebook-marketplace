package com.oldbook.controller.store;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StoreWebController {

    @GetMapping({"/store", "/vendor/books"})
    public String storePage() {
        return "vendor-books";
    }

    @GetMapping("/store-moderation")
    public String moderationPage() {
        return "store-moderation";
    }
}
