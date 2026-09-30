package com.nhom9.oldbook_marketplace.catalog.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SachWebController {

    @GetMapping({"/", "/books"})
    public String homePage(Model model) {
        return "books/catalog";
    }
}