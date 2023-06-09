package cc.mrbird.febs.system.controller;

import cc.mrbird.febs.common.controller.BaseController;
import cc.mrbird.febs.common.domain.FebsResponse;
import cc.mrbird.febs.common.domain.QueryRequest;
import cc.mrbird.febs.common.exception.FebsException;
import cc.mrbird.febs.system.domain.Test;
import cc.mrbird.febs.system.service.TestService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.image.CreateImageRequest;
import com.theokanning.openai.image.Image;
import com.theokanning.openai.service.OpenAiService;
import com.wuwenze.poi.ExcelKit;
import com.wuwenze.poi.handler.ExcelReadHandler;
import com.wuwenze.poi.pojo.ExcelErrorField;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.IntStream;

@Slf4j
@RestController
@RequestMapping("test")
public class TestController extends BaseController {

    private String message;

    @Autowired
    private TestService testService;

    @GetMapping
    public Map<String, Object> findTests(QueryRequest request) {
        Page<Test> page = new Page<>(request.getPageNum(), request.getPageSize());
        return getDataTable(testService.page(page, null));
    }

    /**
     * 生成 Excel导入模板
     */
    @PostMapping("template")
    public void generateImportTemplate(HttpServletResponse response) {
        // 构建数据
        List<Test> list = new ArrayList<>();
        IntStream.range(0, 20).forEach(i -> {
            Test test = new Test();
            test.setField1("字段1");
            test.setField2(i + 1);
            test.setField3("mrbird" + i + "@gmail.com");
            list.add(test);
        });
        // 构建模板
        ExcelKit.$Export(Test.class, response).downXlsx(list, true);
    }

    /**
     * 导入Excel数据，并批量插入 T_TEST表
     */
    @PostMapping("import")
    public FebsResponse importExcels(@RequestParam("file") MultipartFile file) throws FebsException {
        try {
            if (file.isEmpty()) {
                throw new FebsException("导入数据为空");
            }
            String filename = file.getOriginalFilename();
            if (!StringUtils.endsWith(filename, ".xlsx")) {
                throw new FebsException("只支持.xlsx类型文件导入");
            }
            // 开始导入操作
            long beginTimeMillis = System.currentTimeMillis();
            final List<Test> data = Lists.newArrayList();
            final List<Map<String, Object>> error = Lists.newArrayList();
            ExcelKit.$Import(Test.class).readXlsx(file.getInputStream(), new ExcelReadHandler<Test>() {
                @Override
                public void onSuccess(int sheet, int row, Test test) {
                    // 数据校验成功时，加入集合
                    test.setCreateTime(new Date());
                    data.add(test);
                }

                @Override
                public void onError(int sheet, int row, List<ExcelErrorField> errorFields) {
                    // 数据校验失败时，记录到 error集合
                    error.add(ImmutableMap.of("row", row, "errorFields", errorFields));
                }
            });
            if (!data.isEmpty()) {
                // 将合法的记录批量入库
                this.testService.batchInsert(data);
            }
            long time = ((System.currentTimeMillis() - beginTimeMillis));
            ImmutableMap<String, Object> result = ImmutableMap.of(
                    "time", time,
                    "data", data,
                    "error", error
            );
            return new FebsResponse().data(result);
        } catch (Exception e) {
            message = "导入Excel数据失败," + e.getMessage();
            log.error(message);
            throw new FebsException(message);
        }
    }

    /**
     * 导出 Excel
     */
    @PostMapping("export")
    public void export(HttpServletResponse response) throws FebsException {
        try {
            List<Test> list = this.testService.findTests();
            ExcelKit.$Export(Test.class, response).downXlsx(list, false);
        } catch (Exception e) {
            message = "导出Excel失败";
            log.error(message, e);
            throw new FebsException(message);
        }
    }

    @GetMapping("image")
    public List<Image> images() {
        OpenAiService service = new OpenAiService("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");
        CreateImageRequest createImageRequest = CreateImageRequest.builder().prompt("一只可爱动漫小猫咪的头顶开出花朵，当你说你如此的爱我").n(5).size("256x256").user("testing").build();

        List<Image> images = service.createImage(createImageRequest).getData();
        for (Image image : images) {
            System.out.println("image.getUrl() = " + image.getUrl());
        }
        return images;
    }



    @GetMapping("choices")
    public List<ChatCompletionChoice> createChatCompletion(String content,Integer num,Integer length) {
        OpenAiService service = new OpenAiService("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");
        final List<ChatMessage> messages = new ArrayList<>();
//        final ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), "You are a dog and will speak as such.");
        final ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), content);
        messages.add(systemMessage);

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model("gpt-3.5-turbo")
                .messages(messages)
                .n(num)
                .maxTokens(length)
                .logitBias(new HashMap<>())
                .build();

        List<ChatCompletionChoice> choices = service.createChatCompletion(chatCompletionRequest).getChoices();
        System.out.println("choices = " + choices);
        return choices;
    }
}
