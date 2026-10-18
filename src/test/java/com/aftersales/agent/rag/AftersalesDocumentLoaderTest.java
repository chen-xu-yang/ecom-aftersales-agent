package com.aftersales.agent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AftersalesDocumentLoaderTest {

    @Resource
    private AftersalesDocumentLoader aftersalesDocumentLoader;

    @Test
    void loadMarkdowns() {
        aftersalesDocumentLoader.loadMarkdowns();
    }
}