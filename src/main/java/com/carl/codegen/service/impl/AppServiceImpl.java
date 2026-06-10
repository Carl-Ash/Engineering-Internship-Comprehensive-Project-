package com.carl.codegen.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.carl.codegen.model.entity.App;
import com.carl.codegen.mapper.AppMapper;
import com.carl.codegen.service.AppService;
import org.springframework.stereotype.Service;

/**
 * 应用 服务层实现。
 *
 * @author Carl
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

}
