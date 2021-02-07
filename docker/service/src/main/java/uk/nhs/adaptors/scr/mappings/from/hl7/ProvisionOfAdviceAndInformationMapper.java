package uk.nhs.adaptors.scr.mappings.from.hl7;

import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import uk.nhs.adaptors.scr.mappings.from.hl7.common.CommunicationMapper;

import java.util.List;

@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ProvisionOfAdviceAndInformationMapper implements XmlToFhirMapper {

    private final CommunicationMapper communicationMapper;

    @Override
    public List<? extends Resource> map(Node document) {
        return communicationMapper.map(document, "UKCT_MT144049UK01.ProvisionOfAdviceAndInformation");
    }
}
