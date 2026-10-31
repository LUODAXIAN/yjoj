package com.luoyujia.yjoj.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luoyujia.yjoj.client.service.UserFeignClient;
import com.luoyujia.yjoj.common.common.ErrorCode;
import com.luoyujia.yjoj.common.constant.CommonConstant;
import com.luoyujia.yjoj.common.constant.JudgeMqConstants;
import com.luoyujia.yjoj.common.exception.BusinessException;
import com.luoyujia.yjoj.common.utils.SqlUtils;
import com.luoyujia.yjoj.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.luoyujia.yjoj.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.luoyujia.yjoj.model.entity.Question;
import com.luoyujia.yjoj.model.entity.QuestionSubmit;
import com.luoyujia.yjoj.model.entity.User;
import com.luoyujia.yjoj.model.enums.QuestionSubmitLanguageEnum;
import com.luoyujia.yjoj.model.enums.QuestionSubmitStatusEnum;
import com.luoyujia.yjoj.model.vo.QuestionSubmitVO;
import com.luoyujia.yjoj.question.mapper.QuestionSubmitMapper;
import com.luoyujia.yjoj.question.rabbitmq.MyMessageProducer;
import com.luoyujia.yjoj.question.service.QuestionService;
import com.luoyujia.yjoj.question.service.QuestionSubmitService;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @description 针对表【question_submit(题目提交)】的数据库操作Service实现
 */
@Service
public class QuestionSubmitServiceImpl extends ServiceImpl<QuestionSubmitMapper, QuestionSubmit>
    implements QuestionSubmitService {

  @Resource private QuestionService questionService;

  @Resource private UserFeignClient userFeignClient;

  @Resource private MyMessageProducer myMessageProducer;

  /**
   * 提交题目
   *
   * @param questionSubmitAddRequest
   * @param loginUser
   * @return
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public long doQuestionSubmit(QuestionSubmitAddRequest questionSubmitAddRequest, User loginUser) {
    if (questionSubmitAddRequest == null
        || loginUser == null
        || StringUtils.isBlank(questionSubmitAddRequest.getCode())) {
      throw new BusinessException(ErrorCode.PARAMS_ERROR, "提交参数不能为空");
    }
    // 校验编程语言是否合法
    String language = questionSubmitAddRequest.getLanguage();
    QuestionSubmitLanguageEnum languageEnum = QuestionSubmitLanguageEnum.getEnumByValue(language);
    if (languageEnum == null) {
      throw new BusinessException(ErrorCode.PARAMS_ERROR, "编程语言错误");
    }
    long questionId = questionSubmitAddRequest.getQuestionId();
    // 判断实体是否存在，根据类别获取实体
    Question question = questionService.getById(questionId);
    if (question == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
    }
    // 是否已提交题目
    long userId = loginUser.getId();
    // 每个用户串行提交题目
    QuestionSubmit questionSubmit = new QuestionSubmit();
    questionSubmit.setUserId(userId);
    questionSubmit.setQuestionId(questionId);
    questionSubmit.setCode(questionSubmitAddRequest.getCode());
    questionSubmit.setLanguage(language);
    // 设置初始状态
    questionSubmit.setStatus(QuestionSubmitStatusEnum.WAITING.getValue());
    questionSubmit.setJudgeInfo("{}");
    questionSubmit.setJudgeAttempt(0);
    boolean save = this.save(questionSubmit);
    if (!save) {
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据插入失败");
    }
    // 同一事务内原子更新提交数。
    boolean countUpdated =
        questionService.update(
            new LambdaUpdateWrapper<Question>()
                .eq(Question::getId, questionId)
                .setSql("submitNum = submitNum + 1"));
    if (!countUpdated) {
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目提交数更新失败");
    }
    Long questionSubmitId = questionSubmit.getId();
    // 等事务提交后再触发判题，避免异步任务读不到提交记录。
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            myMessageProducer.sendMessage(
                JudgeMqConstants.EXCHANGE,
                JudgeMqConstants.ROUTING_KEY,
                String.valueOf(questionSubmitId));
          }
        });
    return questionSubmitId;
  }

  /**
   * 获取查询包装类（用户根据哪些字段查询，根据前端传来的请求对象，得到 mybatis 框架支持的查询 QueryWrapper 类）
   *
   * @param questionSubmitQueryRequest
   * @return
   */
  @Override
  public QueryWrapper<QuestionSubmit> getQueryWrapper(
      QuestionSubmitQueryRequest questionSubmitQueryRequest) {
    QueryWrapper<QuestionSubmit> queryWrapper = new QueryWrapper<>();
    if (questionSubmitQueryRequest == null) {
      return queryWrapper;
    }
    String language = questionSubmitQueryRequest.getLanguage();
    Integer status = questionSubmitQueryRequest.getStatus();
    Long questionId = questionSubmitQueryRequest.getQuestionId();
    Long userId = questionSubmitQueryRequest.getUserId();
    String sortField = questionSubmitQueryRequest.getSortField();
    String sortOrder = questionSubmitQueryRequest.getSortOrder();

    // 拼接查询条件
    queryWrapper.eq(StringUtils.isNotBlank(language), "language", language);
    queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
    queryWrapper.eq(ObjectUtils.isNotEmpty(questionId), "questionId", questionId);
    queryWrapper.eq(QuestionSubmitStatusEnum.getEnumByValue(status) != null, "status", status);
    queryWrapper.eq("isDelete", false);
    queryWrapper.orderBy(
        SqlUtils.validSortField(sortField),
        CommonConstant.SORT_ORDER_ASC.equals(sortOrder),
        sortField);
    return queryWrapper;
  }

  @Override
  public QuestionSubmitVO getQuestionSubmitVO(QuestionSubmit questionSubmit, User loginUser) {
    QuestionSubmitVO questionSubmitVO = QuestionSubmitVO.objToVo(questionSubmit);
    // 脱敏：仅本人和管理员能看见自己（提交 userId 和登录用户 id 不同）提交的代码
    long userId = loginUser.getId();
    // 处理脱敏
    if (userId != questionSubmit.getUserId() && !userFeignClient.isAdmin(loginUser)) {
      questionSubmitVO.setCode(null);
    }
    return questionSubmitVO;
  }

  @Override
  public Page<QuestionSubmitVO> getQuestionSubmitVOPage(
      Page<QuestionSubmit> questionSubmitPage, User loginUser) {
    List<QuestionSubmit> questionSubmitList = questionSubmitPage.getRecords();
    Page<QuestionSubmitVO> questionSubmitVOPage =
        new Page<>(
            questionSubmitPage.getCurrent(),
            questionSubmitPage.getSize(),
            questionSubmitPage.getTotal());
    if (CollectionUtils.isEmpty(questionSubmitList)) {
      return questionSubmitVOPage;
    }
    List<QuestionSubmitVO> questionSubmitVOList =
        questionSubmitList.stream()
            .map(questionSubmit -> getQuestionSubmitVO(questionSubmit, loginUser))
            .collect(Collectors.toList());
    questionSubmitVOPage.setRecords(questionSubmitVOList);
    return questionSubmitVOPage;
  }

  // 原实现保留供对照；新逻辑增加执行轮次及可靠确认，避免重复生效。
  // @Override
  //     public boolean claimWaitingSubmit(long submitId) {
  //         // 条件更新相当于抢占锁：同一提交只能有一个消费者从 WAITING 进入 RUNNING。
  //         return update(new LambdaUpdateWrapper<QuestionSubmit>()
  //                 .eq(QuestionSubmit::getId, submitId)
  //                 .eq(QuestionSubmit::getStatus, QuestionSubmitStatusEnum.WAITING.getValue())
  //                 .set(QuestionSubmit::getStatus, QuestionSubmitStatusEnum.RUNNING.getValue()));
  //     }
  //
  //     @Override
  //     @Transactional(rollbackFor = Exception.class)
  //     public boolean completeJudge(QuestionSubmit result, boolean accepted) {
  //         // 仅 RUNNING 状态允许完成，防止重复消息重复累计 acceptedNum。
  //         boolean updated = update(result, new LambdaUpdateWrapper<QuestionSubmit>()
  //                 .eq(QuestionSubmit::getId, result.getId())
  //                 .eq(QuestionSubmit::getStatus, QuestionSubmitStatusEnum.RUNNING.getValue()));
  //         if (updated && accepted) {
  //             questionService.update(new LambdaUpdateWrapper<Question>()
  //                     .eq(Question::getId, result.getQuestionId())
  //                     .setSql("acceptedNum = acceptedNum + 1"));
  //         }
  //         return updated;
  //     }
  //
  //     @Override
  //     public boolean failJudge(QuestionSubmit result) {
  //         // 只允许未完成的任务进入 FAILED，已完成结果不能被迟到的异常覆盖。
  //         return update(result, new LambdaUpdateWrapper<QuestionSubmit>()
  //                 .eq(QuestionSubmit::getId, result.getId())
  //                 .in(QuestionSubmit::getStatus,
  //                         QuestionSubmitStatusEnum.WAITING.getValue(),
  //                         QuestionSubmitStatusEnum.RUNNING.getValue()));
  //     }
  @Override
  public boolean claimWaitingSubmit(long submitId, int attempt) {
    return update(
        new LambdaUpdateWrapper<QuestionSubmit>()
            .eq(QuestionSubmit::getId, submitId)
            .eq(QuestionSubmit::getStatus, QuestionSubmitStatusEnum.WAITING.getValue())
            .eq(QuestionSubmit::getJudgeAttempt, attempt)
            .set(QuestionSubmit::getStatus, QuestionSubmitStatusEnum.RUNNING.getValue()));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean completeJudge(QuestionSubmit result, boolean accepted, int attempt) {
    // 状态相同不代表同一次执行，必须同时匹配轮次；条件更新成功才累计通过数。
    boolean updated =
        update(
            result,
            new LambdaUpdateWrapper<QuestionSubmit>()
                .eq(QuestionSubmit::getId, result.getId())
                .eq(QuestionSubmit::getStatus, QuestionSubmitStatusEnum.RUNNING.getValue())
                .eq(QuestionSubmit::getJudgeAttempt, attempt));
    if (updated && accepted) {
      boolean counted =
          questionService.update(
              new LambdaUpdateWrapper<Question>()
                  .eq(Question::getId, result.getQuestionId())
                  .setSql("acceptedNum = acceptedNum + 1"));
      if (!counted) throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目通过数更新失败");
    }
    return updated;
  }

  @Override
  public boolean failJudge(QuestionSubmit result, int attempt) {
    return update(
        result,
        new LambdaUpdateWrapper<QuestionSubmit>()
            .eq(QuestionSubmit::getId, result.getId())
            .eq(QuestionSubmit::getJudgeAttempt, attempt)
            .in(
                QuestionSubmit::getStatus,
                QuestionSubmitStatusEnum.WAITING.getValue(),
                QuestionSubmitStatusEnum.RUNNING.getValue()));
  }

  @Override
  public boolean prepareRetry(long submitId, int currentAttempt) {
    if (currentAttempt < 0 || currentAttempt >= JudgeMqConstants.MAX_ATTEMPT) return false;
    // 单次原子操作改变状态和轮次；重复请求不会重复加一。
    return update(
        new LambdaUpdateWrapper<QuestionSubmit>()
            .eq(QuestionSubmit::getId, submitId)
            .eq(QuestionSubmit::getStatus, QuestionSubmitStatusEnum.RUNNING.getValue())
            .eq(QuestionSubmit::getJudgeAttempt, currentAttempt)
            .set(QuestionSubmit::getStatus, QuestionSubmitStatusEnum.WAITING.getValue())
            .set(QuestionSubmit::getJudgeInfo, "{}")
            .setSql("judgeAttempt = judgeAttempt + 1"));
  }
}
