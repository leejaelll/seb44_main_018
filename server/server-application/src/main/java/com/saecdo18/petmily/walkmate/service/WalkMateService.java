package com.saecdo18.petmily.walkmate.service;

import com.saecdo18.petmily.member.dto.MemberDto;
import com.saecdo18.petmily.member.entity.Member;
import com.saecdo18.petmily.member.repository.MemberRepository;
import com.saecdo18.petmily.walkmate.dto.WalkMateCommentDto;
import com.saecdo18.petmily.walkmate.dto.WalkMateDto;
import com.saecdo18.petmily.walkmate.entity.WalkMate;
import com.saecdo18.petmily.walkmate.entity.WalkMateComment;
import com.saecdo18.petmily.walkmate.entity.WalkMateLike;
import com.saecdo18.petmily.walkmate.mapper.WalkMateMapper;
import com.saecdo18.petmily.walkmate.repository.WalkLikeRepository;
import com.saecdo18.petmily.walkmate.repository.WalkMateCommentRepository;
import com.saecdo18.petmily.walkmate.repository.WalkMateRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class WalkMateService {

    private final WalkMateRepository walkMateRepository;
    private final WalkMateCommentRepository walkMateCommentRepository;
    private final WalkLikeRepository walkLikeRepository;
    private final MemberRepository memberRepository;
    private final WalkMateCommentService walkMateCommentService;
    private final WalkMateMapper walkMateMapper;

    public WalkMateService(WalkMateRepository walkMateRepository,
                           WalkMateCommentRepository walkMateCommentRepository,
                           WalkLikeRepository walkLikeRepository,
                           MemberRepository memberRepository,
                           WalkMateCommentService walkMateCommentService,
                           WalkMateMapper walkMateMapper) {
        this.walkMateRepository = walkMateRepository;
        this.walkMateCommentRepository = walkMateCommentRepository;
        this.walkLikeRepository = walkLikeRepository;
        this.memberRepository = memberRepository;
        this.walkMateCommentService = walkMateCommentService;
        this.walkMateMapper = walkMateMapper;
    }

    public WalkMateDto.Response createWalk(WalkMate walkMate, long memberId) {

        Member member = methodFindByMemberId(memberId);
        walkMate.setMember(member);
        walkMateRepository.save(walkMate);

        WalkMateDto.Response response = walkMateMapper.walkMateToWalkMateResponseDto(walkMate);

        MemberDto.Info info = getMemberInfoByWalk(walkMate);
        response.setMemberInfo(info);

        return response;
    }

    public WalkMateDto.Response findWalk(long walkMateId){

        WalkMate walk = methodFindByWalkId(walkMateId);

        WalkMateDto.Response response = walkMateMapper.walkMateToWalkMateResponseDto(walk);
        List<WalkMateComment> allComments = walkMateCommentService.findCommentsByWalkId(walkMateId);
        List<WalkMateCommentDto.Response> comments = allComments.stream()
                .map(comment -> walkMateCommentService.findComment(comment.getWalkMateCommentId()))
                .collect(Collectors.toList());

        response.setComments(comments);

        MemberDto.Info info = getMemberInfoByWalk(walk);
        response.setMemberInfo(info);

        return response;
    }

    public List<WalkMateDto.Response> findCommentedWalks(long memberId){

        //1. 해당 memberId의 모든 comment 가져오기
        List<WalkMateComment> myCommentsList = walkMateCommentService.findCommentsByMemberId(memberId);
        List<WalkMate> myWalkMateList = myCommentsList.stream()
                .map(comment -> comment.getWalkMate())
                .collect(Collectors.toList());

        //2. 각각의 comment의 postId를 가져오기
        //3. 그 포스트 Id들 중에 중복을 제거하여 리스트로 만들어두기
        //4. 각각을 walkMate로 변환하기

        List<WalkMate> myUniqueWalkmateList = removeDuplicates(myWalkMateList);

        //5. Dto.Response로 가공

        List<WalkMateDto.Response> responseList=new ArrayList<>();

        for(WalkMate walk : myUniqueWalkmateList){
            WalkMateDto.Response response=walkMateMapper.walkMateToWalkMateResponseDto(walk);
            MemberDto.Info info = getMemberInfoByWalk(walk);
            response.setMemberInfo(info);
            responseList.add(response);
        }

        return responseList;
    }

    public static <T> List<T> removeDuplicates(List<T> list){
        return list.stream()
                .distinct()
                .collect(Collectors.toList());
    }

    public List<WalkMate> findWalks(){

        return walkMateRepository.findAll();
    }

    public void deleteWalk(long walkMateId, long memberId){

        WalkMate walk = methodFindByWalkId(walkMateId);

        if(memberId!=walk.getMember().getMemberId()){
            throw new IllegalArgumentException("삭제할 권한이 없습니다.");
        }

        for(WalkMateComment comment : walk.getComments()){
            walkMateCommentRepository.delete(comment);
        }

        walk.setComments(null);
        walkMateRepository.delete(walk);
    }

    public WalkMateDto.Response updateWalkMate(WalkMateDto.Patch walkPatchDto,
                                   long walkId, long memberId){

        WalkMate walk = methodFindByWalkId(walkId);

        if(memberId!=walk.getMember().getMemberId()){
            throw new IllegalArgumentException("수정할 권한이 없습니다.");
        }

        walk.updateWalk(
                walkPatchDto.getTitle(),
                walkPatchDto.getContent(),
                walkPatchDto.getMapURL(),
                walkPatchDto.getChatURL(),
                walkPatchDto.getLocation(),
                walkPatchDto.getTime(),
                walkPatchDto.getOpen(),
                walkPatchDto.getMaximum());

        WalkMateDto.Response response = walkMateMapper.walkMateToWalkMateResponseDto(walk);

        MemberDto.Info info = getMemberInfoByWalk(walk);
        response.setMemberInfo(info);
        walkMateRepository.save(walk);

        return response;
    }

    public WalkMateDto.Like likeByMember(long walkId, long memberId){
        WalkMate findWalk = methodFindByWalkId(walkId);
        Member finMember = methodFindByMemberId(memberId);

        Optional<WalkMateLike> optionalWalkMateLike =
                walkLikeRepository.findByWalkAndMember(findWalk, finMember);

        WalkMateLike walkMateLike;
        if(optionalWalkMateLike.isEmpty()){
            walkMateLike = WalkMateLike.builder()
                    .walk(findWalk)
                    .member(finMember)
                    .build();
            findWalk.likeCount(true);
        } else {
            walkMateLike = optionalWalkMateLike.get();
            walkMateLike.updateIsLikes();
            findWalk.likeCount(walkMateLike.isLike());
        }
        WalkMateLike savedWalkMateLike = walkLikeRepository.save(walkMateLike);
        WalkMate savedWalk = walkMateRepository.save(findWalk);

        return WalkMateDto.Like.builder()
                .likeCount(savedWalk.getLikeCount())
                .isLike(savedWalkMateLike.isLike())
                .build();
    }

    public List<WalkMate> searchWalksMatchWithLocation(String location){

        List<WalkMate> allWalks = findWalks();
        List<WalkMate> matchWalks = allWalks.stream()
                .filter(walk -> walk.getLocation().equals(location))
                .collect(Collectors.toList());

        return matchWalks;
    }

    public List<WalkMate> recentPage(int page, int size, String location, boolean openFilter){


        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<WalkMate> response = walkMateRepository.findByLocation(pageRequest, location).getContent();

        if(openFilter){
            onlyOpenWalk(response);
        }

        return response;

    }

    public WalkMateDto.Open changeOpenStatus(boolean status, long walkId, long memberId){

        WalkMate walk = methodFindByWalkId(walkId);

        if(memberId!=walk.getMember().getMemberId()){
            throw new IllegalArgumentException("수정할 권한이 없습니다.");
        }

        WalkMateDto.Open response = WalkMateDto.Open.builder()
                .walkMatePostId(walkId)
                .open(status)
                .build();

        return response;
    }

    public List<WalkMate> onlyOpenWalk(List<WalkMate> walkMates){

        List<WalkMate> allWalks = findWalks();
        List<WalkMate> openWalks = allWalks.stream()
                .filter(walk -> walk.getOpen().equals(true))
                .collect(Collectors.toList());

        return openWalks;
    }

    //-------------------------------------------------------------------//

    private WalkMate methodFindByWalkId(long walkId){
        return walkMateRepository.findById(walkId).orElseThrow(
                () -> new RuntimeException("산책 게시글을 찾을 수 없습니다.")
        );
    }

    private Member methodFindByMemberId(long memberId){
        return memberRepository.findById(memberId).orElseThrow(
                () -> new RuntimeException("사용자를 찾을 수 없습니다.")
        );
    }

    private MemberDto.Info memberIdToMemberInfoDto(long memberId){
        Member findMember = memberRepository.findById(memberId).orElseThrow(
                () -> new RuntimeException("사용자를 찾을 수 없습니다.")
        );

        return MemberDto.Info.builder()
                .memberId(findMember.getMemberId())
                .imageURL(findMember.getImageURL())
                .nickname(findMember.getNickname())
                .build();
    }

    private MemberDto.Info getMemberInfoByWalk(WalkMate walk) {
        Member member = methodFindByMemberId(walk.getMember().getMemberId());
        MemberDto.Info info = MemberDto.Info.builder()
                .memberId(member.getMemberId())
                .imageURL(member.getImageURL())
                .nickname(member.getNickname())
                .build();
        return info;
    }

}