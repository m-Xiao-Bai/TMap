package com.mu.transitmap.controller;


import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.impl.CountryServiceImpl;
import com.mu.transitmap.vo.CountrySelectIdNameVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 国家信息表 前端控制器
 * </p>
 *
 * @author muxiaobai
 * @since 2026-01-22
 */
@RestController
@RequestMapping("/country")
public class CountryController {

    @Autowired
    private CountryServiceImpl countryService;

    /**
     * 国家下拉接口
     * */
    @GetMapping("/list_id")
    public Result allListId(){
        List<CountrySelectIdNameVO> countrySelectIdNameVOS = countryService.allListId();
        return Result.success(countrySelectIdNameVOS);
    }
}
