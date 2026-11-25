package com.zhongbang.controller.service.repair;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.common.util.Jsons;
import com.zhongbang.controller.mapper.customer.CustomerAssetMapper;
import com.zhongbang.controller.mapper.customer.CustomerMapper;
import com.zhongbang.controller.mapper.payment.PaymentBillMapper;
import com.zhongbang.controller.mapper.repair.RepairLineMapper;
import com.zhongbang.controller.mapper.repair.RepairTicketMapper;
import com.zhongbang.controller.mapper.technician.TechnicianMapper;
import com.zhongbang.controller.mapper.technician.TechnicianScheduleMapper;
import com.zhongbang.controller.mapper.workorder.WorkOrderMapper;
import com.zhongbang.controller.pojo.customer.entity.Customer;
import com.zhongbang.controller.pojo.customer.entity.CustomerAsset;
import com.zhongbang.controller.pojo.order.dto.OrderRequests;
import com.zhongbang.controller.pojo.order.vo.OrderVo;
import com.zhongbang.controller.pojo.payment.entity.PaymentBill;
import com.zhongbang.controller.pojo.repair.dto.RepairRequests;
import com.zhongbang.controller.pojo.repair.entity.RepairLine;
import com.zhongbang.controller.pojo.repair.entity.RepairTicket;
import com.zhongbang.controller.pojo.repair.vo.RepairVo;
import com.zhongbang.controller.pojo.technician.entity.Technician;
import com.zhongbang.controller.pojo.technician.entity.TechnicianSchedule;
import com.zhongbang.controller.pojo.workorder.entity.WorkOrder;
import com.zhongbang.controller.security.LoginUser;
import com.zhongbang.controller.security.UserContext;
import com.zhongbang.controller.security.UserType;
import com.zhongbang.controller.service.notice.NoticeService;
import com.zhongbang.controller.service.technician.TechnicianRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 报修的业务入口。小程序提交、我的报修，以及管理端报价、派工、完工、取消都从这里进。
 *
 * <p>报价和收款规则在 {@link RepairRules}。这里负责查设备、占师傅档期、写通知。
 * 新报修会在事务提交后推到管理端 {@code /api/admin/stream}。
 */
@Service
public class RepairService {

    private final RepairTicketMapper repairTicketMapper;
    private final RepairLineMapper repairLineMapper;
    private final PaymentBillMapper paymentBillMapper;
    private final CustomerMapper customerMapper;
    private final CustomerAssetMapper customerAssetMapper;
    private final TechnicianMapper technicianMapper;
    private final TechnicianScheduleMapper scheduleMapper;
    private final WorkOrderMapper workOrderMapper;
    private final NoticeService noticeService;
    private final IdentifierGenerator idGenerator;

    public RepairService(RepairTicketMapper repairTicketMapper,
                         RepairLineMapper repairLineMapper,
                         PaymentBillMapper paymentBillMapper,
                         CustomerMapper customerMapper,
                         CustomerAssetMapper customerAssetMapper,
                         TechnicianMapper technicianMapper,
                         TechnicianScheduleMapper scheduleMapper,
                         WorkOrderMapper workOrderMapper,
                         NoticeService noticeService,
                         IdentifierGenerator idGenerator) {
        this.repairTicketMapper = repairTicketMapper;
        this.repairLineMapper = repairLineMapper;
        this.paymentBillMapper = paymentBillMapper;
        this.customerMapper = customerMapper;
        this.customerAssetMapper = customerAssetMapper;
        this.technicianMapper = technicianMapper;
        this.scheduleMapper = scheduleMapper;
        this.workOrderMapper = workOrderMapper;
        this.noticeService = noticeService;
        this.idGenerator = idGenerator;
    }

    /** 这张应付单是不是维修费。收款接口据此决定走报修还是订单。 */
    public boolean ownsPayment(long paymentId) {
        PaymentBill bill = paymentBillMapper.selectById(paymentId);
        return bill != null && "REPAIR".equals(bill.getBizType());
    }

    @Transactional
    public RepairVo.Created create(RepairRequests.Create request) {
        LoginUser user = requireCustomer();
        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少添加一台设备");
        }
        Customer customer = customerMapper.selectById(user.getUserId());
        LocalDateTime now = DateTimes.now();
        RepairTicket ticket = new RepairTicket();
        ticket.setId(nextId());
        ticket.setTenantId(1L);
        ticket.setTicketNo(nextTicketNo());
        ticket.setCustomerId(user.getUserId());
        ticket.setCustomerName(firstNonBlank(customer == null ? null : customer.getName(), user.getDisplayName(), "客户"));
        ticket.setStatus("SUBMITTED");
        ticket.setExpectDate(blankToNull(request.getExpectDate()));
        ticket.setExpectSlot(blankToNull(request.getExpectSlot()));
        ticket.setContactMobile(firstNonBlank(request.getContactMobile(), customer == null ? null : customer.getMobile(), ""));
        ticket.setRemark(blankToNull(request.getRemark()));
        ticket.setPayableAmount(0L);
        ticket.setPaidAmount(0L);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);

        List<RepairLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (RepairRequests.Line incoming : request.getLines()) {
            lines.add(buildLine(ticket, incoming, lineNo, user.getUserId()));
            lineNo += 1;
        }
        repairTicketMapper.insert(ticket);
        for (RepairLine line : lines) {
            repairLineMapper.insert(line);
        }
        noticeService.repairCreated(ticket, lines.size());
        RepairBundle bundle = new RepairBundle();
        bundle.setTicket(ticket);
        bundle.setLines(lines);
        return RepairAssembler.created(bundle);
    }

    public PageResult<RepairVo.MineItem> myList(String status, int page, int size) {
        LoginUser user = requireCustomer();
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        LambdaQueryWrapper<RepairTicket> wrapper = new LambdaQueryWrapper<RepairTicket>()
                .eq(RepairTicket::getCustomerId, user.getUserId())
                .orderByDesc(RepairTicket::getId);
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status.trim())) {
            wrapper.eq(RepairTicket::getStatus, status.trim());
        }
        Page<RepairTicket> result = repairTicketMapper.selectPage(new Page<>(p, s), wrapper);
        List<RepairVo.MineItem> list = hydrate(result.getRecords()).stream().map(RepairAssembler::mineItem).toList();
        return PageResult.of(list, result.getTotal(), p, s);
    }

    public RepairVo.Mine myDetail(long id) {
        LoginUser user = requireCustomer();
        RepairBundle bundle = load(id);
        if (!user.getUserId().equals(bundle.getTicket().getCustomerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报修单不存在");
        }
        return RepairAssembler.mine(bundle);
    }

    /** 客户同意或拒绝报价。管理端报价后保外已经是待付款，再点同意不会重复生成应付。 */
    @Transactional
    public RepairVo.Status confirmQuote(long id, RepairRequests.Confirm request) {
        LoginUser user = requireCustomer();
        RepairBundle bundle = load(id);
        if (!user.getUserId().equals(bundle.getTicket().getCustomerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报修单不存在");
        }
        boolean accept = request == null || request.getAccept() == null || request.getAccept();
        if (!accept) {
            String reason = request.getRemark() == null || request.getRemark().isBlank() ? "客户拒绝报价" : request.getRemark().trim();
            RepairRules.cancel(bundle, reason);
            save(bundle);
            return new RepairVo.Status(bundle.getTicket().getStatus(), RepairAssembler.statusText(bundle.getTicket().getStatus()));
        }
        RepairTicket ticket = bundle.getTicket();
        if (ticket.getQuoteAmount() == null && "SUBMITTED".equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "尚未报价");
        }
        if ("QUOTED".equals(ticket.getStatus())) {
            long quote = ticket.getQuoteAmount() == null ? 0L : ticket.getQuoteAmount();
            if (ticket.getInWarranty() != null && ticket.getInWarranty() == 1 || quote <= 0) {
                ticket.setStatus("CONFIRMED");
                ticket.setPayableAmount(0L);
            } else {
                boolean hasWait = bundle.getPayments().stream().anyMatch(payment -> "WAIT".equals(payment.getStatus()));
                if (!hasWait) {
                    int sortNo = 1;
                    for (PaymentBill payment : bundle.getPayments()) {
                        if (payment.getSortNo() != null && payment.getSortNo() >= sortNo) {
                            sortNo = payment.getSortNo() + 1;
                        }
                    }
                    bundle.addPayment(RepairRules.buildPayment(bundle, this::nextId, quote, sortNo, DateTimes.nowText()));
                }
                ticket.setPayableAmount(quote);
                ticket.setStatus("PENDING_PAYMENT");
                RepairRules.sync(bundle);
            }
            save(bundle);
        }
        return new RepairVo.Status(ticket.getStatus(), RepairAssembler.statusText(ticket.getStatus()));
    }

    public PageResult<RepairVo.Admin> adminList(String status, String keyword, int page, int size) {
        requireStaff();
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        LambdaQueryWrapper<RepairTicket> wrapper = new LambdaQueryWrapper<RepairTicket>()
                .orderByDesc(RepairTicket::getCreatedAt);
        if (status != null && !status.isBlank()) {
            wrapper.eq(RepairTicket::getStatus, status.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = keyword.trim();
            List<Long> lineHits = repairLineMapper.selectList(new LambdaQueryWrapper<RepairLine>()
                            .like(RepairLine::getBrand, like)
                            .or().like(RepairLine::getModel, like)
                            .or().like(RepairLine::getSn, like))
                    .stream().map(RepairLine::getTicketId).distinct().toList();
            wrapper.and(w -> {
                w.like(RepairTicket::getTicketNo, like)
                        .or().like(RepairTicket::getCustomerName, like)
                        .or().like(RepairTicket::getContactMobile, like);
                if (!lineHits.isEmpty()) {
                    w.or().in(RepairTicket::getId, lineHits);
                }
            });
        }
        Page<RepairTicket> result = repairTicketMapper.selectPage(new Page<>(p, s), wrapper);
        List<RepairVo.Admin> list = hydrate(result.getRecords()).stream().map(RepairAssembler::admin).toList();
        return PageResult.of(list, result.getTotal(), p, s);
    }

    public RepairVo.Admin adminGet(long id) {
        requireStaff();
        return RepairAssembler.admin(load(id));
    }

    @Transactional
    public RepairVo.Admin quote(long id, RepairRequests.Quote request) {
        requireStaff();
        RepairBundle bundle = load(id);
        RepairRules.quote(bundle, request, this::nextId);
        save(bundle);
        return RepairAssembler.admin(bundle);
    }

    /** 报价确认或付款之后，按一行派师傅，并占用档期。 */
    @Transactional
    public RepairVo.Admin assign(long id, RepairRequests.Assign request) {
        requireStaff();
        if (request == null || request.getTechnicianId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "师傅不存在");
        }
        Technician technician = TechnicianRules.requireAssignable(technicianMapper.selectById(request.getTechnicianId()));
        if (request.getAppointAt() == null || request.getAppointAt().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写上门时间");
        }
        String appointAt = request.getAppointAt().trim();
        if (!appointAt.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上门时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
        RepairBundle bundle = load(id);
        RepairRules.assertAssignable(bundle.getTicket());
        RepairLine line = RepairRules.findLine(bundle, request.getLineId());
        Slot next = slotFromAppoint(appointAt);
        Slot previous = line.getAppointAt() == null ? null : slotFromAppoint(line.getAppointAt());
        boolean same = technician.getId().equals(line.getTechnicianId())
                && previous != null
                && previous.date.equals(next.date)
                && previous.slot.equals(next.slot);
        if (!same) {
            if (line.getTechnicianId() != null && previous != null) {
                releaseSchedule(line.getTechnicianId(), previous.date, previous.slot);
            }
            occupySchedule(technician.getId(), next.date, next.slot, bundle.getTicket().getTicketNo(), bundle.getTicket().getCustomerName());
        }
        line.setTechnicianId(technician.getId());
        line.setTechnicianName(technician.getName());
        line.setAppointAt(appointAt);
        if (line.getWorkOrderId() == null) {
            WorkOrder work = insertWorkOrder(bundle.getTicket(), line, technician, appointAt);
            line.setWorkOrderId(work.getId());
        } else {
            WorkOrder work = workOrderMapper.selectById(line.getWorkOrderId());
            if (work != null) {
                work.setTechnicianId(technician.getId());
                work.setAppointmentAt(LocalDateTime.parse(appointAt, DateTimes.FMT));
                work.setUpdatedAt(DateTimes.now());
                workOrderMapper.updateById(work);
            }
        }
        bundle.getTicket().setStatus("IN_SERVICE");
        bundle.getTicket().setUpdatedAt(DateTimes.now());
        save(bundle);
        return RepairAssembler.admin(bundle);
    }

    @Transactional
    public RepairVo.Admin complete(long id) {
        requireStaff();
        RepairBundle bundle = load(id);
        RepairRules.complete(bundle);
        LocalDateTime now = DateTimes.now();
        for (RepairLine line : bundle.getLines()) {
            if (line.getWorkOrderId() == null) {
                continue;
            }
            WorkOrder work = workOrderMapper.selectById(line.getWorkOrderId());
            if (work != null) {
                work.setStatus("DONE");
                work.setUpdatedAt(now);
                workOrderMapper.updateById(work);
            }
        }
        save(bundle);
        return RepairAssembler.admin(bundle);
    }

    @Transactional
    public RepairVo.Admin cancel(long id, OrderRequests.Cancel request) {
        requireStaff();
        RepairBundle bundle = load(id);
        RepairRules.cancel(bundle, request == null ? null : request.getReason());
        save(bundle);
        return RepairAssembler.admin(bundle);
    }

    @Transactional
    public OrderVo.Payment offlineConfirm(long paymentId, OrderRequests.OfflineConfirm request) {
        LoginUser staff = requireStaff();
        PaymentBill bill = paymentBillMapper.selectById(paymentId);
        if (bill == null || bill.getBizId() == null || !"REPAIR".equals(bill.getBizType())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "应付单不存在");
        }
        RepairBundle bundle = load(bill.getBizId());
        RepairRules.confirmOffline(bundle, paymentId, request, staff.getUserId(), staff.getDisplayName(), this::nextId);
        save(bundle);
        return RepairAssembler.admin(bundle).payments().stream()
                .filter(item -> paymentId == item.id())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应付单不存在"));
    }

    private RepairLine buildLine(RepairTicket ticket, RepairRequests.Line incoming, int lineNo, long customerId) {
        if (incoming == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "每行需 assetId 或 external");
        }
        RepairLine line = new RepairLine();
        line.setId(nextId());
        line.setTicketId(ticket.getId());
        line.setLineNo(lineNo);
        line.setFaultType(blankToNull(incoming.getFaultType()) == null ? "OTHER" : incoming.getFaultType().trim());
        if (incoming.getAssetId() != null) {
            CustomerAsset asset = customerAssetMapper.selectById(incoming.getAssetId());
            if (asset == null || !Long.valueOf(customerId).equals(asset.getCustomerId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "设备不存在");
            }
            String desc = blankToNull(incoming.getFaultDesc());
            List<String> images = clean(incoming.getImages());
            if (desc == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写故障描述");
            }
            if (images.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请上传故障照片");
            }
            line.setAssetId(asset.getId());
            line.setBrand(asset.getBrand() == null ? "" : asset.getBrand());
            line.setModel(asset.getModel());
            line.setSn(asset.getSn());
            line.setSource(asset.getSource() == null || asset.getSource().isBlank() ? "INTERNAL" : asset.getSource());
            line.setFaultDesc(desc);
            line.setImagesJson(Jsons.writeStrings(images));
            boolean in = asset.getWarrantyEnd() != null && !asset.getWarrantyEnd().isBefore(LocalDate.now());
            line.setInWarranty(in ? 1 : 0);
            return line;
        }
        RepairRequests.External external = incoming.getExternal();
        if (external == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "每行需 assetId 或 external");
        }
        String brand = blankToNull(external.getBrand());
        String model = blankToNull(external.getModel());
        String desc = firstNonBlank(incoming.getFaultDesc(), external.getFaultDesc(), "");
        if (brand == null || model == null || desc.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "外购设备须填写品牌、型号、故障描述");
        }
        List<String> images = clean(incoming.getImages());
        if (images.isEmpty()) {
            images = clean(external.getImages());
        }
        if (images.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "外购设备须上传现场照片");
        }
        String sn = blankToNull(external.getSn());
        if (sn != null) {
            CustomerAsset taken = customerAssetMapper.selectOne(new LambdaQueryWrapper<CustomerAsset>()
                    .eq(CustomerAsset::getSn, sn)
                    .last("LIMIT 1"));
            if (taken != null) {
                throw new BusinessException(ErrorCode.CONFLICT, "该机身码已存在");
            }
        }
        String proof = firstNonBlank(external.getPurchaseProofUrl(), external.getPurchaseProof(), "");
        CustomerAsset asset = new CustomerAsset();
        asset.setId(nextId());
        asset.setTenantId(1L);
        asset.setCustomerId(customerId);
        asset.setBrand(brand);
        asset.setModel(model);
        asset.setSn(sn);
        asset.setSource("EXTERNAL");
        asset.setBuyDate(LocalDate.now());
        asset.setPurchaseChannel(blankToNull(external.getPurchaseChannel()));
        asset.setPurchaseProofUrl(proof.isEmpty() ? null : proof);
        asset.setCreatedAt(DateTimes.now());
        customerAssetMapper.insert(asset);
        line.setAssetId(asset.getId());
        line.setBrand(brand);
        line.setModel(model);
        line.setSn(sn);
        line.setSource("EXTERNAL");
        line.setFaultDesc(desc);
        line.setImagesJson(Jsons.writeStrings(images));
        line.setPurchaseChannel(asset.getPurchaseChannel());
        line.setPurchaseProofUrl(asset.getPurchaseProofUrl());
        line.setInWarranty(0);
        return line;
    }

    private WorkOrder insertWorkOrder(RepairTicket ticket, RepairLine line, Technician technician, String appointAt) {
        WorkOrder work = new WorkOrder();
        work.setId(nextId());
        work.setTenantId(1L);
        work.setWorkNo("WR" + line.getId());
        work.setType("REPAIR");
        work.setAssetId(line.getAssetId());
        work.setCustomerId(ticket.getCustomerId());
        work.setTechnicianId(technician.getId());
        work.setStatus("ASSIGNED");
        work.setQuoteAmount(line.getLineQuoteAmount() == null ? 0L : line.getLineQuoteAmount());
        work.setActualAmount(0L);
        work.setAppointmentAt(LocalDateTime.parse(appointAt, DateTimes.FMT));
        work.setCreatedAt(DateTimes.now());
        work.setUpdatedAt(DateTimes.now());
        workOrderMapper.insert(work);
        return work;
    }

    private void occupySchedule(long technicianId, String date, String slot, String ticketNo, String customerName) {
        TechnicianSchedule row = scheduleMapper.selectOne(new LambdaQueryWrapper<TechnicianSchedule>()
                .eq(TechnicianSchedule::getTechnicianId, technicianId)
                .eq(TechnicianSchedule::getServiceDate, date)
                .eq(TechnicianSchedule::getTimeSlot, slot)
                .last("LIMIT 1"));
        if (row == null) {
            row = new TechnicianSchedule();
            row.setId(nextId());
            row.setTechnicianId(technicianId);
            row.setServiceDate(date);
            row.setTimeSlot(slot);
            row.setCapacity(2);
            row.setUsedCount(0);
            row.setOrderNo(ticketNo);
            row.setCustomerName(customerName);
            scheduleMapper.insert(row);
        }
        int used = row.getUsedCount() == null ? 0 : row.getUsedCount();
        int capacity = row.getCapacity() == null ? 2 : row.getCapacity();
        if (used >= capacity) {
            throw new BusinessException(ErrorCode.SLOT_CONFLICT, "师傅档期冲突");
        }
        row.setUsedCount(used + 1);
        if (row.getOrderNo() == null) {
            row.setOrderNo(ticketNo);
        }
        if (row.getCustomerName() == null) {
            row.setCustomerName(customerName);
        }
        scheduleMapper.updateById(row);
    }

    private void releaseSchedule(Long technicianId, String date, String slot) {
        if (technicianId == null || date == null || slot == null) {
            return;
        }
        TechnicianSchedule row = scheduleMapper.selectOne(new LambdaQueryWrapper<TechnicianSchedule>()
                .eq(TechnicianSchedule::getTechnicianId, technicianId)
                .eq(TechnicianSchedule::getServiceDate, date)
                .eq(TechnicianSchedule::getTimeSlot, slot)
                .last("LIMIT 1"));
        if (row != null && row.getUsedCount() != null && row.getUsedCount() > 0) {
            row.setUsedCount(row.getUsedCount() - 1);
            scheduleMapper.updateById(row);
        }
    }

    private RepairBundle load(long id) {
        RepairTicket ticket = repairTicketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报修单不存在");
        }
        List<RepairBundle> bundles = hydrate(List.of(ticket));
        return bundles.get(0);
    }

    private List<RepairBundle> hydrate(List<RepairTicket> tickets) {
        if (tickets.isEmpty()) {
            return List.of();
        }
        List<Long> ids = tickets.stream().map(RepairTicket::getId).toList();
        List<RepairLine> lines = repairLineMapper.selectList(new LambdaQueryWrapper<RepairLine>()
                .in(RepairLine::getTicketId, ids)
                .orderByAsc(RepairLine::getLineNo));
        List<PaymentBill> payments = paymentBillMapper.selectList(new LambdaQueryWrapper<PaymentBill>()
                .eq(PaymentBill::getBizType, "REPAIR")
                .in(PaymentBill::getBizId, ids));
        List<RepairBundle> bundles = new ArrayList<>();
        for (RepairTicket ticket : tickets) {
            RepairBundle bundle = new RepairBundle();
            bundle.setTicket(ticket);
            bundle.setLines(new ArrayList<>(lines.stream().filter(line -> ticket.getId().equals(line.getTicketId())).toList()));
            bundle.setPayments(new ArrayList<>(payments.stream()
                    .filter(bill -> ticket.getId().equals(bill.getBizId()))
                    .sorted(Comparator.comparingInt(bill -> bill.getSortNo() == null ? 0 : bill.getSortNo()))
                    .toList()));
            bundles.add(bundle);
        }
        return bundles;
    }

    private void save(RepairBundle bundle) {
        RepairTicket ticket = bundle.getTicket();
        ticket.setUpdatedAt(DateTimes.now());
        repairTicketMapper.updateById(ticket);
        for (RepairLine line : bundle.getLines()) {
            repairLineMapper.updateById(line);
        }
        for (PaymentBill bill : bundle.getPayments()) {
            if (bundle.getFreshPayments().contains(bill)) {
                paymentBillMapper.insert(bill);
            } else {
                paymentBillMapper.updateById(bill);
            }
        }
    }

    private String nextTicketNo() {
        String day = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "RT" + day;
        Long count = repairTicketMapper.selectCount(new LambdaQueryWrapper<RepairTicket>().likeRight(RepairTicket::getTicketNo, prefix));
        return prefix + String.format("%04d", (count == null ? 0 : count) + 1);
    }

    private long nextId() {
        return idGenerator.nextId(null).longValue();
    }

    /** 用小时划分上午、下午、晚上，和订单派工占用的是同一张档期表。 */
    private Slot slotFromAppoint(String appointAt) {
        String date = appointAt.substring(0, 10);
        int hour = Integer.parseInt(appointAt.substring(11, 13));
        String slot = hour < 12 ? "上午" : hour < 18 ? "下午" : "晚上";
        return new Slot(date, slot);
    }

    private LoginUser requireCustomer() {
        LoginUser user = UserContext.require();
        if (user.getUserType() != UserType.CUSTOMER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请使用客户账号");
        }
        return user;
    }

    private LoginUser requireStaff() {
        LoginUser user = UserContext.require();
        if (user.getUserType() != UserType.STAFF) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    private static List<String> clean(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Slot(String date, String slot) {
    }
}
