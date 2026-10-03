package com.oldbook.controller.catalog;

import com.oldbook.dto.catalog.SachSearchRequest;
import com.oldbook.service.catalog.SachService;
import com.oldbook.exception.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class SachWebController {

    private final SachService sachService;

    public SachWebController(SachService sachService) {
        this.sachService = sachService;
    }

    @GetMapping({"/", "/books"})
    public String homePage(Model model) {
        return "books/catalog";
    }

    @GetMapping("/books/tim-kiem")
    public String searchPage(@ModelAttribute("filters") SachSearchRequest request, Model model) {
        model.addAttribute("books", sachService.search(request));
        model.addAttribute("categories", sachService.getDanhMuc());
        return "books/search";
    }

    @GetMapping("/books/{maSach}")
    public ModelAndView detailPage(@PathVariable Integer maSach) {
        try {
            return new ModelAndView("books/detail", "book", sachService.getDetail(maSach));
        } catch (BusinessException exception) {
            ModelAndView result = new ModelAndView("books/not-found");
            result.setStatus(HttpStatus.NOT_FOUND);
            return result;
        }
    }
}
