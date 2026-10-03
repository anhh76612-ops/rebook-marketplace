package com.oldbook.controller.shipping;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ShippingWebController {
    @GetMapping("/shipping")
    public String shippingPage() {
        return "shipping/dashboard";
    }
}
