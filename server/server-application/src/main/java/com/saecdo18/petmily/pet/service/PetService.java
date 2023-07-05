package com.saecdo18.petmily.pet.service;

import com.saecdo18.petmily.awsConfig.S3UploadService;
import com.saecdo18.petmily.feed.dto.FeedCommentDto;
import com.saecdo18.petmily.feed.dto.FeedDto;
import com.saecdo18.petmily.feed.entity.Feed;
import com.saecdo18.petmily.feed.entity.FeedImage;
import com.saecdo18.petmily.image.dto.ImageDto;
import com.saecdo18.petmily.image.entity.Image;
import com.saecdo18.petmily.image.repository.ImageRepository;
import com.saecdo18.petmily.member.entity.Member;
import com.saecdo18.petmily.member.repository.MemberRepository;
import com.saecdo18.petmily.member.service.MemberService;
import com.saecdo18.petmily.pet.entity.PetImage;
import com.saecdo18.petmily.pet.mapper.PetMapper;
import com.saecdo18.petmily.pet.dto.PetDto;
import com.saecdo18.petmily.pet.entity.Pet;
import com.saecdo18.petmily.pet.repository.PetImageRepository;
import com.saecdo18.petmily.pet.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Transactional
@Service
@RequiredArgsConstructor
public class PetService {
    private final PetRepository petRepository;
    private final PetMapper petMapper;
    private final MemberRepository memberRepository;
    private final PetImageRepository petImageRepository;
    private final ImageRepository imageRepository;
    private final S3UploadService s3UploadService;
    private final MemberService memberService;

    public PetDto.Response createPet(long memberId, PetDto.Post petPostDto) throws IOException {
        Member findMember = methodFindByMemberIdMember(memberId);

        Pet pet = Pet.nonePetIdAndMessage()
                        .name(petPostDto.getName())
                        .age(petPostDto.getAge())
                        .sex(petPostDto.getSex())
                        .species(petPostDto.getSpecies())
                        .information(petPostDto.getInformation())
                        .walkMated(petPostDto.isWalkMated())
                        .build();

        pet.updateMember(findMember);

        if (!petPostDto.getImages().isEmpty()) {
            for (MultipartFile multipartFile : petPostDto.getImages()) {
                String originalFilename = multipartFile.getOriginalFilename();
                String uploadFileURL = s3UploadService.saveFile(multipartFile);
                savePetImage(pet, originalFilename, uploadFileURL);
            }
        }
        Pet savePet = petRepository.save(pet);

        if(!findMember.isAnimalParents()){
            findMember.updateAnimalParents(true);
        }

        return getPet(savePet.getPetId());
    }

    public PetDto.Response getPet(long petId){
        Pet findPet = methodFindByPetId(petId);
//        PetDto.Response response = petMapper.petToPetResponseDto(findPet);

        return changePetToPetDtoResponse(findPet);
    }


    public PetDto.Response updatePet(long memberId, long petId, Pet patchPet){
        Pet findPet = methodFindByPetId(petId);
        if(memberId == findPet.getMember().getMemberId()){
            findPet.updatePatch(
                    patchPet.getName(),
                    patchPet.getAge(),
                    patchPet.getSex(),
                    patchPet.getSpecies(),
                    patchPet.getInformation(),
                    patchPet.isWalkMated());

//            Pet.PetBuilder findPetBuilder = findPet.nonePetIdAndMessage();
//            findPetBuilder.profile(patchPet.getProfile());
//            findPetBuilder.name(patchPet.getName());
//            findPetBuilder.age(patchPet.getAge());
//            findPetBuilder.sex(patchPet.getSex());
//            findPetBuilder.species(patchPet.getSpecies());
//            findPetBuilder.information(patchPet.getInformation());
//            findPetBuilder.walkMated(patchPet.isWalkMated());


        }
        else {
            throw new RuntimeException("반려동물의 수정권한이 없습니다.");
        }

        PetDto.Response response = petMapper.petToPetResponseDto(findPet);
        return response;
    }

    private PetDto.Response changePetToPetDtoResponse(Pet pet) {
        PetDto.Response response = petMapper.petToPetResponseDto(pet);

        response.setMemberId(pet.getMember().getMemberId());
        List<PetImage> petImageList = petImageRepository.findByPet(pet);
        response.setImages(petImageToImageDtoList(petImageList));

        return response;
    }

    private List<ImageDto> petImageToImageDtoList(List<PetImage> petImageList) {
        List<ImageDto> imageDtoList = new ArrayList<>();
        for (PetImage petImage : petImageList) {
            Image image = petImage.getImage();
            imageDtoList.add(petMapper.imageToImageDto(image));
        }
        return imageDtoList;
    }



    public void savePetImage(Pet pet, String originalFilename, String uploadFileURL) {
        Image image = Image.builder()
                .originalFilename(originalFilename)
                .uploadFileURL(uploadFileURL)
                .build();
        Image saveImage = imageRepository.save(image);
        PetImage petImage = PetImage.builder()
                .pet(pet)
                .image(saveImage)
                .build();

        petImageRepository.save(petImage);
    }

    private Pet methodFindByPetId(long petId) {
        return petRepository.findById(petId).orElseThrow(() -> new RuntimeException("찾으시는 반려동물이 없습니다"));
    }


    private Member methodFindByMemberIdMember(long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("해당 견주가 존재하지 않습니다"));
    }
}
