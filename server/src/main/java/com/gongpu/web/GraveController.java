package com.gongpu.web;

import com.gongpu.security.AuthInterceptor;
import com.gongpu.state.GraveService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 墓园：当前账号的全部碑。 */
@RestController
@RequestMapping("/api/graveyard")
public class GraveController {

    private final GraveService graves;

    public GraveController(GraveService graves) {
        this.graves = graves;
    }

    @GetMapping
    public List<Map<String, Object>> list(HttpServletRequest req) {
        return graves.list(AuthInterceptor.userId(req));
    }
}
