package com.wuba.crm.tx.tcc.coordinator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TCC Coordinator 独立进程入口。
 *
 * <p>Coordinator 只持有 TCC 状态库，不写参与方业务库。</p>
 */
@SpringBootApplication
public class TccCoordinatorApplication {

    /**
     * 启动 Coordinator。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(TccCoordinatorApplication.class, args);
    }
}
