package ethan.entelect.textract.demo;

import ethan.entelect.textract.demo.controllers.DocumentController;
import ethan.entelect.textract.demo.services.AmazonService;
import lombok.AllArgsConstructor;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AllArgsConstructor
class ApplicationTests {

//    private MockMvc mockMvc;
//
//    @Mock
//    private AmazonService amazonService;
//
//    @InjectMocks
//    private DocumentController documentController;
//
//    @Test
//    void contextLoads() {
//    }
//
//    @Test
//    void badRequest() throws Exception {
//        ResponseEntity<Map<String, String>> response = documentController.analyzeData(null);
//        this.mockMvc.perform(get("/"))
//                .andDo(print())
//                .andExpect(status().isOk())
//                .andExpect(content().string(containsString("Hello World!")));
//    }

}
